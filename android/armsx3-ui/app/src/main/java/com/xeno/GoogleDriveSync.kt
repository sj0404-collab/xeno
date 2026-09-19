package com.xeno

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream

/**
 * Google Drive sync for XENO saves.
 *
 * Uses Google Drive API v3 with OAuth 2.0. Files are stored in a hidden
 * app-specific folder: "appDataFolder" (hidden from user, per-app quota).
 * Alternative: a visible folder "XENO Saves" in the user's Drive root.
 *
 * Layout on Drive:
 *   appDataFolder/saves/<titleId>.zip          — one zip per save title
 *   appDataFolder/games/<remoteName>           — game ISOs/PKGs (streaming)
 *
 * The Drive API handles auth, resumable uploads, and partial downloads.
 * No WebDAV/PROPFIND needed — Drive has native list/search.
 */
object GoogleDriveSync {
    private const val TAG = "GoogleDriveSync"

    private const val SCOPE_DRIVE_APPDATA = DriveScopes.DRIVE_APPDATA
    private const val SCOPE_DRIVE_FILE = DriveScopes.DRIVE_FILE

    private const val PrefAccountName = "gdrive.sync.account"
    private const val PrefAutoPush = "gdrive.sync.auto"

    @Volatile
    private var _accountName: String? = null

    @Volatile
    var autoPush: Boolean = false

    private var _driveService: Drive? = null
    private var _signInClient: GoogleSignInClient? = null

    // The app folder ID is always "appDataFolder" for the hidden app folder
    private const val APP_FOLDER = "appDataFolder"

    private const val SAVES_FOLDER_NAME = "saves"
    private const val GAMES_FOLDER_NAME = "games"
    private const val MAX_ARCHIVE_BYTES = 1L shl 30 // 1 GiB
    private const val MAX_ARCHIVE_ENTRIES = 100_000

    private val syncMutex = kotlinx.coroutines.sync.Mutex()

    /** Minimal config holder — only the account name is needed to reconstruct the Drive service. */
    data class Config(val accountName: String?)

    /** Snapshot of config + autoPush for off-main-thread workers. */
    data class Snapshot(val config: Config?, val autoPush: Boolean)

    /** Capture current config + autoPush atomically for background workers. */
    fun snapshot(): Snapshot {
        val cfg = Config(_accountName)
        return Snapshot(cfg, if (cfg == null) false else autoPush)
    }

    /** Initialize from saved preferences. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("xeno_prefs", Context.MODE_PRIVATE)
        val account = prefs.getString(PrefAccountName, null)?.takeIf { it.isNotBlank() }
        _accountName = account
        autoPush = prefs.getBoolean(PrefAutoPush, false)

        if (account != null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(SCOPE_DRIVE_APPDATA, SCOPE_DRIVE_FILE)
            )
            credential.selectedAccount = Account(account, "com.google")
            val transport: HttpTransport = NetHttpTransport()
            _driveService = Drive.Builder(transport, GsonFactory(), credential)
                .setApplicationName("XENO")
                .build()

            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(SCOPE_DRIVE_APPDATA), Scope(SCOPE_DRIVE_FILE))
                .requestEmail()
                .build()
            _signInClient = GoogleSignIn.getClient(context, signInOptions)
        }
    }

    /** Save account name and auto-push preference. */
    fun save(context: Context, accountName: String?, auto: Boolean = autoPush) {
        val prefs = context.getSharedPreferences("xeno_prefs", Context.MODE_PRIVATE)
        _accountName = accountName?.takeIf { it.isNotBlank() }
        autoPush = auto
        if (accountName == null || accountName.isBlank()) {
            prefs.edit().remove(PrefAccountName).remove(PrefAutoPush).apply()
            _driveService = null
            _signInClient = null
        } else {
            prefs.edit()
                .putString(PrefAccountName, accountName)
                .putBoolean(PrefAutoPush, auto)
                .apply()
            load(context) // re-init service
        }
    }

    /** Current signed-in account name, or null. */
    fun accountName(): String? = _accountName

    /** Whether user is signed in. */
    fun isSignedIn(): Boolean = _accountName != null && _driveService != null

    /** Get the sign-in intent to launch via ActivityResultLauncher. */
    fun getSignInIntent(activity: android.app.Activity): Intent {
        val client = _signInClient ?: throw IllegalStateException("GoogleDriveSync not initialized")
        return client.signInIntent
    }

    /** Handle sign-in result from onActivityResult. */
    fun handleSignInResult(data: Intent?, context: Context): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            _accountName = account.email
            save(context, account.email, autoPush)
            true
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed: ${e.statusCode}", e)
            false
        }
    }

    /** Sign out and clear credentials. */
    fun signOut(context: Context) {
        _signInClient?.signOut()
        save(context, null, false)
    }

    /** Update auto-push preference. */
    fun updateAutoPush(enabled: Boolean) {
        autoPush = enabled
        val context = com.xeno.runtime.MainActivityRuntime.applicationContext
            ?: return
        val prefs = context.getSharedPreferences("xeno_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PrefAutoPush, enabled).apply()
    }

    private suspend fun getDriveService(): Drive = withContext(Dispatchers.IO) {
        _driveService ?: throw IllegalStateException("Google Drive not signed in")
    }

    private suspend fun getOrCreateFolder(name: String, parentId: String = APP_FOLDER): String = withContext(Dispatchers.IO) {
        val service = getDriveService()
        val query = "mimeType='application/vnd.google-apps.folder' and name='$name' and '$parentId' in parents and trashed=false"
        val list = service.files().list()
            .setQ(query)
            .setSpaces("appDataFolder")
            .setFields("files(id,name)")
            .execute()
        list.files.firstOrNull()?.id ?: service.files().create(File().apply {
            name = name
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }).setFields("id").execute().id
    }

    private suspend fun getSavesFolder(): String = getOrCreateFolder(SAVES_FOLDER_NAME)
    private suspend fun getGamesFolder(): String = getOrCreateFolder(GAMES_FOLDER_NAME)

    /** Upload one save title's archive to Drive. */
    internal suspend fun uploadArchive(zip: java.io.File, titleId: String): Boolean = syncMutex.withLock {
        try {
            val service = getDriveService()
            val savesFolder = getSavesFolder()
            val fileName = "$titleId.zip"

            // Check if file exists
            val query = "name='$fileName' and '$savesFolder' in parents and trashed=false"
            val existing = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(id)")
                .execute()

            val mediaContent = com.google.api.client.http.FileContent("application/zip", zip)
            val driveFile = File().apply { name = fileName; parents = listOf(savesFolder) }

            if (existing.files.isNotEmpty()) {
                service.files().update(existing.files[0].id, driveFile, mediaContent).execute()
            } else {
                service.files().create(driveFile, mediaContent)
                    .setFields("id")
                    .execute()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "uploadArchive failed for $titleId", e)
            false
        }
    }

    /** Download save archive for titleId and extract to savedata. */
    suspend fun downloadSaves(titleId: String): Boolean = syncMutex.withLock {
        try {
            val service = getDriveService()
            val savesFolder = getSavesFolder()
            val fileName = "$titleId.zip"

            val query = "name='$fileName' and '$savesFolder' in parents and trashed=false"
            val list = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(id)")
                .execute()

            val fileId = list.files.firstOrNull()?.id ?: return false

            val staged = java.io.File(RPCSX.rootDirectory + "cache/sync-stage", "gdrive-$titleId.zip")
            staged.parentFile?.mkdirs()

            // Download
            service.files().get(fileId)
                .executeMediaAndDownloadTo(staged)

            // Extract using same logic as CloudSync
            val dest = SaveDataImporter.savedataRoot() ?: return false
            val stageName = ".$titleId.gdrive.staging"
            if (!unzipSaveArchive(staged, dest, stageName)) return false

            val stagedDir = java.io.File(dest, stageName)
            val target = java.io.File(dest, titleId)
            val backup = java.io.File(dest, ".$titleId.old")
            val swapped = try {
                if (backup.exists() && !backup.deleteRecursively()) false
                else if (!target.exists()) stagedDir.renameTo(target)
                else if (target.renameTo(backup)) {
                    if (stagedDir.renameTo(target)) {
                        backup.deleteRecursively()
                        true
                    } else {
                        backup.renameTo(target)
                        false
                    }
                } else false
            } catch (e: Exception) {
                Log.e(TAG, "save swap failed for $titleId", e)
                false
            }
            if (!swapped) java.io.File(dest, stageName).deleteRecursively()
            swapped
        } catch (e: Exception) {
            Log.e(TAG, "downloadSaves failed for $titleId", e)
            false
        }
    }

    /** Push all local saves to Drive. */
    suspend fun pushAllSaves(): Int = syncMutex.withLock {
        val root = SaveDataImporter.savedataRoot() ?: return@withLock 0
        var pushed = 0
        root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .forEach { dir ->
                val zip = saveArchive(dir, dir.name) ?: return@forEach
                if (uploadArchive(zip, dir.name)) pushed++
                zip.delete()
            }
        pushed
    }

    /** Pull all saves from Drive (all titles in saves folder + local titles). */
    suspend fun pullAllSaves(): Int = syncMutex.withLock {
        val root = SaveDataImporter.savedataRoot() ?: return@withLock 0
        val local = root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { it.name }

        val remote = listRemoteSaves()

        val targets = java.util.LinkedHashSet<String>()
        targets.addAll(local)
        targets.addAll(remote)

        var pulled = 0
        targets.forEach { if (downloadSaves(it)) pulled++ }
        pulled
    }

    /** List all save titles in Drive saves folder. */
    private suspend fun listRemoteSaves(): List<String> = withContext(Dispatchers.IO) {
        try {
            val service = getDriveService()
            val savesFolder = getSavesFolder()
            val query = "mimeType='application/zip' and '$savesFolder' in parents and trashed=false"
            val list = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(name)")
                .execute()
            list.files.mapNotNull { it.name?.takeIf { it.endsWith(".zip") }?.substringBeforeLast(".") }
        } catch (e: Exception) {
            Log.w(TAG, "listRemoteSaves failed", e)
            emptyList()
        }
    }

    /** Download a game file from Drive games folder. */
    suspend fun downloadGame(remoteName: String): String? = withContext(Dispatchers.IO) {
        val safeRemote = com.xeno.CloudSync.safeComponent(remoteName)
        if (safeRemote.isEmpty()) return null

        val local = java.io.File(RPCSX.rootDirectory + "games", safeRemote.substringAfterLast('/'))
        local.parentFile?.mkdirs()

        try {
            val service = getDriveService()
            val gamesFolder = getGamesFolder()
            val query = "name='$safeRemote' and '$gamesFolder' in parents and trashed=false"
            val list = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(id,size)")
                .execute()

            val file = list.files.firstOrNull() ?: return null

            // Check local cache
            if (local.exists()) {
                val remoteSize = file.size?.toLong() ?: -1
                if (remoteSize >= 0 && local.length() == remoteSize) {
                    return local.absolutePath
                }
                if (remoteSize < 0 && local.length() > 0) {
                    Log.i(TAG, "offline fallback: launching cached $safeRemote")
                    return local.absolutePath
                }
            }

            // Download with resumable media
            service.files().get(file.id!!)
                .executeMediaAndDownloadTo(local)

            local.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "downloadGame failed for $remoteName", e)
            runCatching { local.delete() }
            null
        }
    }

    /** Get streaming URL for a game (Drive supports Range requests via alt=media). */
    suspend fun streamGameUrl(remoteName: String): String? = withContext(Dispatchers.IO) {
        val safeRemote = com.xeno.CloudSync.safeComponent(remoteName)
        if (safeRemote.isEmpty()) return null

        try {
            val service = getDriveService()
            val gamesFolder = getGamesFolder()
            val query = "name='$safeRemote' and '$gamesFolder' in parents and trashed=false"
            val list = service.files().list()
                .setQ(query)
                .setSpaces("appDataFolder")
                .setFields("files(id)")
                .execute()

            val file = list.files.firstOrNull() ?: return null
            // Drive supports Range requests on the alt=media endpoint
            "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media"
        } catch (e: Exception) {
            Log.w(TAG, "streamGameUrl failed for $remoteName", e)
            null
        }
    }

    // ---- Shared archive logic (copied from CloudSync) ----

    internal fun saveArchive(dir: java.io.File, titleId: String): java.io.File? {
        val staged = java.io.File(RPCSX.rootDirectory + "cache/sync-stage")
        staged.mkdirs()
        val out = java.io.File(staged, "$titleId-${System.nanoTime()}.zip")
        try {
            var total = 0L
            var entries = 0
            ZipOutputStream(FileOutputStream(out)).use { zos ->
                for (f in dir.walkTopDown()) {
                    val rel = dir.toPath().relativize(f.toPath()).toString()
                    if (!f.isFile || rel.endsWith(".tmp")) continue
                    if (entries++ >= MAX_ARCHIVE_ENTRIES) break
                    zos.putNextEntry(ZipEntry(rel))
                    FileInputStream(f).use { fis ->
                        val buf = ByteArray(8192)
                        var n: Int
                        while (fis.read(buf).also { n = it } != -1) {
                            total += n
                            zos.write(buf, 0, n)
                        }
                    }
                    zos.closeEntry()
                    if (total > MAX_ARCHIVE_BYTES) break
                }
            }
            if (total > MAX_ARCHIVE_BYTES || entries >= MAX_ARCHIVE_ENTRIES) {
                Log.w(TAG, "save archive for $titleId exceeds caps (${total}B/$entries entries); skipping")
                out.delete()
                return null
            }
            return out
        } catch (e: Exception) {
            Log.e(TAG, "archive failed for $titleId", e)
            runCatching { out.delete() }
            return null
        }
    }

    private fun unzipSaveArchive(zipFile: java.io.File, destRoot: java.io.File, titleId: String): Boolean {
        val target = java.io.File(destRoot, titleId)
        target.mkdirs()
        val targetCanonical = target.absoluteFile.canonicalPath + java.io.File.separator
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    val candidate = entry.name.replace('\\', '/')
                    if (candidate.isEmpty() ||
                        candidate.startsWith("/") ||
                        candidate.split('/').any { it == ".." || it == "." }
                    ) {
                        Log.w(TAG, "skipping zip entry with unsafe name '${entry.name}' in $titleId")
                        zin.closeEntry()
                        entry = zin.nextEntry
                        continue
                    }
                    val outFile = java.io.File(target, candidate)
                    if (!outFile.absoluteFile.canonicalPath.startsWith(targetCanonical)) {
                        Log.w(TAG, "skipping zip entry escaping save root '${entry.name}' in $titleId")
                        zin.closeEntry()
                        entry = zin.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zin.copyTo(it) }
                    }
                    entry = zin.nextEntry
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "unzip failed for $titleId", e)
            target.deleteRecursively()
            return false
        }
    }
}