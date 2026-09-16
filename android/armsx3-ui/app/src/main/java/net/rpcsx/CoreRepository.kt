package net.rpcsx

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class CoreStatus {
    None,
    Downloading,
    Ready,
    Failed,
}

/**
 * The emulator core is not shipped inside the APK (it would be ~60 MB; the APK
 * carries only the JNI glue and assets). Winlator-style, the core is a separate
 * `libxeno-core.so` asset on the same GitHub release as the APK, downloaded on
 * demand and kept in the app's PRIVATE internal directory -- the only place
 * dlopen() may map it for execution. External/SD storage is mounted noexec and
 * would refuse to load it.
 *
 * The pairing contract: a release ships exactly one variant of APK and core
 * (a13, armv8.2 + dotprod + fp16, API 33), both tagged with the same version, so
 * the "latest release" download is always ISA-compatible with the installed APP.
 * The JNI glue resolves the .so from that folder (see Rpcs3Bridge), which is the
 * same absolute-path dlopen it always used against nativeLibraryDir.
 */
class CoreRepository {
    companion object {
        const val CORE_LIB_NAME = "libxeno-core.so"

        // The repo that hosts both the APK and the core releases.
        private const val GitHubRepo = "sj0404-collab/xeno"
        private const val LATEST_URL = "https://api.github.com/repos/$GitHubRepo/releases/latest"

        val status: MutableState<CoreStatus> = mutableStateOf(CoreStatus.None)
        val error: MutableState<String?> = mutableStateOf(null)

        /** Total/current bytes of the in-flight download, for a progress bar. */
        val progressTotal: MutableState<Long> = mutableStateOf(0L)
        val progressRead: MutableState<Long> = mutableStateOf(0L)

        /** Private app data dir holding the downloaded core. Exec-safe. */
        fun dir(context: Context): File =
            context.getDir("xeno-core", Context.MODE_PRIVATE)

        fun coreFile(context: Context): File =
            File(dir(context), CORE_LIB_NAME)

        fun isInstalled(context: Context): Boolean {
            val f = coreFile(context)
            return f.isFile && f.length() > 0L
        }

        /**
         * Resolve the core download URL from the latest GitHub release.
         * The asset name is fixed, so no variant matching is needed.
         */
        @Throws(Exception::class)
        private fun resolveCoreUrl(): String {
            val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
            return try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "XENO-Core")
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("GitHub API ${conn.responseCode}")
                }
                val release = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val assets = release.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name") == CORE_LIB_NAME) {
                        return asset.getString("browser_download_url")
                    }
                }
                throw java.io.IOException("$CORE_LIB_NAME not found in the latest release")
            } finally {
                conn.disconnect()
            }
        }

        /**
         * Download the core into the private dir, streaming with progress. The file
         * is validated as a loadable core via [RPCSX.getLibraryVersion] before it is
         * accepted -- a truncated transfer must never surface as "core did not load".
         */
        suspend fun download(context: Context): Boolean = withContext(Dispatchers.IO) {
            status.value = CoreStatus.Downloading
            error.value = null
            progressRead.value = 0L
            progressTotal.value = 0L

            val target = coreFile(context)
            val partial = File(dir(context).apply { mkdirs() }, ".$CORE_LIB_NAME.part")
            val coreUrl = runCatching { resolveCoreUrl() }.getOrNull()

            if (coreUrl == null) {
                status.value = CoreStatus.Failed
                error.value = "Could not find the emulator core on GitHub. Check your connection and try again."
                return@withContext false
            }

            val http = URL(coreUrl).openConnection() as HttpURLConnection
            try {
                http.connectTimeout = 15_000
                http.readTimeout = 30_000
                http.setRequestProperty("User-Agent", "XENO-Core")
                if (http.responseCode != HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Download failed (HTTP ${http.responseCode})")
                }
                val total = http.contentLengthLong
                progressTotal.value = total
                var read = 0L
                http.inputStream.use { input ->
                    partial.outputStream().use { sink ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            read += n
                            progressRead.value = read
                        }
                    }
                }
                // A mid-transfer drop looks like a clean EOF -- verify the byte count.
                if (total > 0 && read != total) {
                    throw java.io.IOException("Incomplete download: $read of $total bytes")
                }
                if (read == 0L) {
                    throw java.io.IOException("Downloaded core is empty")
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                // Only claim readiness for a library the glue can actually use.
                if (RPCSX.instance.getLibraryVersion(target.absolutePath) == null) {
                    target.delete()
                    throw java.io.IOException("Downloaded core failed to load")
                }
                status.value = CoreStatus.Ready
                return@withContext true
            } catch (e: Exception) {
                runCatching { partial.delete() }
                status.value = CoreStatus.Failed
                error.value = e.message ?: "Core download failed"
                return@withContext false
            } finally {
                http.disconnect()
            }
        }
    }
}