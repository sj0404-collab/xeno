package com.xeno.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xeno.config.Settings
import com.xeno.config.Dev9HostMapping
import com.xeno.i18n.I18n
import com.xeno.i18n.str
import com.xeno.ui.Colors
import com.xeno.ui.InGameOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.rpcsx.ProgressRepository
import net.rpcsx.RPCSX
import java.net.NetworkInterface

/**
 * DEV9 networking/HDD settings brought over from OG XENO's SettingsActivity.
 *
 * Android's useful backend is PCSX2's socket backend. PCAP options are kept
 * visible for parity/debugging, but normal users should leave the API on
 * Sockets and the adapter on Auto. DEV9 is initialized at VM boot, so these
 * settings are persisted immediately and take effect on the next game/BIOS
 * launch.
 */
@Composable
fun NetworkTab(state: MutableState<Settings>) {
    val s = state.value
    val scroll = settingsScrollState()
    ControllerAutoScroll(scroll)
    fun apply(updated: Settings) = InGameOverlay.saveSettings(updated)

    // The PS2 networking that used to live here -- DEV9 Ethernet, the HDD image,
    // per-game DNS host lists and Local Link (PS2 System Link over UDP) -- was
    // all emulating a PS2 expansion bay. The PS3 has none of it: RPCS3 models
    // networking at the PSN/RPCN level instead, which is these four controls.
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleRow(
                str("net.internet.label"),
                s.ps3.netEnabled,
                description = str("net.internet.description"),
        ) { apply(s.copy(ps3 = s.ps3.copy(netEnabled = it))) }
        SettingsDivider()
        // Three states, not two. This was a toggle that could only reach Disconnected and
        // Simulated, so RPCN -- the one that actually connects, and whose client has been
        // compiled into the core all along -- had no way of being selected.
        SegmentedGridRow(
                label = str("net.psn.label"),
                options = listOf(
                        str("net.psn.off"),
                        str("net.psn.simulated"),
                        str("net.psn.rpcn"),
                ),
                selectedIndex = s.ps3.psnStatus.coerceIn(0, 2),
                columns = 3,
                description = str("net.psn.description"),
                onChange = { apply(s.copy(ps3 = s.ps3.copy(psnStatus = it))) },
        )
        // The account lives behind RPCN, so only offer it once RPCN is the choice --
        // otherwise it invites people to set up an account the emulator will not use.
        if (s.ps3.psnStatus == 2) {
            SettingsDivider()
            RpcnAccountSection()
            SettingsDivider()
            RpcnFriendsSection()
        }
        SettingsDivider()
        ToggleRow(
                str("net.upnp.label"),
                s.ps3.upnpEnabled,
                description = str("net.upnp.description"),
        ) { apply(s.copy(ps3 = s.ps3.copy(upnpEnabled = it))) }
        SettingsDivider()
        // DNS and the redirect list come first because they are the two that answer "how do
        // I reach a custom game server" -- RPCN covers Sony's side only, and a publisher's
        // own backend was never part of it.
        EditableTextRow(
                controllerId = "net.dns",
                label = str("net.dns.label"),
                value = s.ps3.dnsAddress,
                description = str("net.dns.description"),
                placeholder = "8.8.8.8",
        ) { apply(s.copy(ps3 = s.ps3.copy(dnsAddress = it.ifBlank { "8.8.8.8" }))) }
        SettingsDivider()
        EditableTextRow(
                controllerId = "net.swap",
                label = str("net.swap.label"),
                value = s.ps3.ipSwapList,
                description = str("net.swap.description"),
        ) { apply(s.copy(ps3 = s.ps3.copy(ipSwapList = it))) }
        SettingsDivider()
        EditableTextRow(
                controllerId = "net.ip",
                label = str("net.ip.label"),
                value = s.ps3.ipAddress,
                description = str("net.ip.description"),
                placeholder = "0.0.0.0",
        ) { apply(s.copy(ps3 = s.ps3.copy(ipAddress = it.ifBlank { "0.0.0.0" }))) }
        SettingsDivider()
        EditableTextRow(
                controllerId = "net.bind",
                label = str("net.bind.label"),
                value = s.ps3.bindAddress,
                description = str("net.bind.description"),
                placeholder = "0.0.0.0",
        ) { apply(s.copy(ps3 = s.ps3.copy(bindAddress = it.ifBlank { "0.0.0.0" }))) }
        SettingsDivider()
        EditableTextRow(
                controllerId = "net.country",
                label = str("net.country.label"),
                value = s.ps3.psnCountry,
                description = str("net.country.description"),
                placeholder = "us",
        ) { apply(s.copy(ps3 = s.ps3.copy(psnCountry = it.ifBlank { "us" }.lowercase().take(2)))) }
        SettingsDivider()
        ToggleRow(
                str("net.mac.label"),
                s.ps3.deriveMacFromPsid,
                description = str("net.mac.description"),
        ) { apply(s.copy(ps3 = s.ps3.copy(deriveMacFromPsid = it))) }
        SettingsDivider()
        ToggleRow(
                str("net.clans.label"),
                s.ps3.clansEnabled,
                description = str("net.clans.description"),
        ) { apply(s.copy(ps3 = s.ps3.copy(clansEnabled = it))) }
        SettingsDivider()
        // Emulate USB Keyboard. Previously reachable ONLY from the in-game pause menu, which made
        // the "On-Screen Keyboard (toggle)" hotkey's own message a dead end: it says to turn this
        // on in Network settings, and there was nothing here to turn on. Same field, so the two
        // rows stay in sync.
        ToggleRow(
                str("network.emulateUsbKeyboard"),
                s.usbKeyboard,
                description = str("net.usbKeyboard.description"),
        ) { apply(s.copy(usbKeyboard = it)) }
        SettingsDivider()
        CloudSection()
    }
}

/**
 * Cloud save + cloud game mirroring (see [com.xeno.CloudSync]).
 *
 * Saves sync over WebDAV (or any plain HTTP host that keeps a file tree) —
 * <remote>/saves/<titleId>.zip — and games stream from <remote>/games/. Save data is
 * pushed on demand and, when the toggle is on, automatically when a game exits.
 *
 * The transport is plain HTTPS + Basic auth in the URL, so it also works on a
 * bare static server or a sync folder; no SDK is required on the other end.
 */
private const val CloudGlyph = "☁"
private const val CloudUrlKey = "cloud.tab.url"
private const val CloudUserKey = "cloud.tab.user"
private const val CloudPassKey = "cloud.tab.pass"

/** Free WebDAV hosts prefilled by one tap in [CloudSection]. */
private data class CloudProviderPreset(
    val url: String,
    val labelKey: String,
    val hintKey: String,
)

private val CLOUD_PROVIDERS = listOf(
    CloudProviderPreset(
        url = "https://webdav.pcloud.com/",
        labelKey = "cloud.provider.pcloud",
        hintKey = "cloud.provider.pcloud.hint",
    ),
    CloudProviderPreset(
        url = "https://app.koofr.net/dav/",
        labelKey = "cloud.provider.koofr",
        hintKey = "cloud.provider.koofr.hint",
    ),
    CloudProviderPreset(
        url = "https://webdav.cloud.mail.ru/",
        labelKey = "cloud.provider.mailru",
        hintKey = "cloud.provider.mailru.hint",
    ),
    CloudProviderPreset(
        url = "https://webdav.yandex.ru/",
        labelKey = "cloud.provider.yandex",
        hintKey = "cloud.provider.yandex.hint",
    ),
    CloudProviderPreset(
        url = "https://your-nextcloud.example/remote.php/dav/files/username/",
        labelKey = "cloud.provider.nextcloud",
        hintKey = "cloud.provider.nextcloud.hint",
    ),
)

@Composable
private fun CloudSection() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // The whole section is driven off CloudSync.config, which is volatile and may
    // change underneath us; these mirrors keep the text fields sane while editing.
    var url by remember { mutableStateOf(com.xeno.CloudSync.config?.remoteUrl ?: "") }
    var user by remember { mutableStateOf(com.xeno.CloudSync.config?.username.orEmpty()) }
    var pass by remember { mutableStateOf(com.xeno.CloudSync.config?.password.orEmpty()) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CloudGlyph, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                str("cloud.section.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            str("cloud.section.description"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        // One-tap free WebDAV hosts: most users do not own a server, and a preset that fills
        // the address turns "set up WebDAV" into "create a free account and paste your login".
        // Credentials are never guessed — only the URL template is prefilled.
        Text(
            str("cloud.providers.label"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in CLOUD_PROVIDERS) {
                // Resolve the strings here (composition context); onClick is not composable.
                val presetLabel = str(p.labelKey)
                val presetHint = str(p.hintKey)
                OutlinedButton(
                    onClick = { url = p.url; status = presetHint },
                    modifier = Modifier
                        .weight(1f)
                        .controllerFocusable(
                            "cloud.provider.${p.labelKey}",
                            onConfirm = { url = p.url },
                        ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 10.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Text(
                        presetLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            str("cloud.providers.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        CloudEditRow(
            controllerId = "cloud.url",
            label = str("cloud.url.label"),
            description = str("cloud.url.description"),
            placeholder = "https://nextcloud.example.com/remote.php/dav/xeno/",
            value = url,
            fieldLabel = str("cloud.url.fieldLabel"),
            onChange = { url = it },
        )
        if (url.isNotBlank()) {
            CloudEditRow(
                controllerId = "cloud.user",
                label = str("cloud.user.label"),
                description = str("cloud.user.description"),
                value = user,
                fieldLabel = str("cloud.user.fieldLabel"),
                onChange = { user = it },
            )
            CloudEditRow(
                controllerId = "cloud.pass",
                label = str("cloud.pass.label"),
                description = str("cloud.pass.description"),
                value = pass,
                fieldLabel = str("cloud.pass.fieldLabel"),
                onChange = { pass = it },
            )
        }

        // Saving writes credentials to app-private SharedPreferences. Show that the config
        // changed rather than hiding the secret behind a save that never happens.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val savedLabel = str("cloud.status.saved")
            val disabledLabel = str("cloud.status.disabled")
            val save = {
                val updated = if (url.isBlank()) null
                else com.xeno.CloudSync.Config(
                    remoteUrl = url,
                    username = user.trim().takeIf { it.isNotBlank() },
                    password = pass.takeIf { it.isNotBlank() },
                )
                com.xeno.CloudSync.save(updated)
                status = if (updated == null) disabledLabel else savedLabel
            }
            OutlinedButton(
                onClick = save,
                modifier = Modifier.weight(1f).controllerFocusable("cloud.save", onConfirm = save),
            ) { Text(str("cloud.save")) }
            if (com.xeno.CloudSync.config != null) {
                val clearDisabled = str("cloud.status.disabled")
                val clear = {
                    com.xeno.CloudSync.save(null)
                    url = ""; user = ""; pass = ""
                    status = clearDisabled
                }
                OutlinedButton(
                    onClick = clear,
                    modifier = Modifier.weight(1f).controllerFocusable("cloud.clear", onConfirm = clear),
                ) { Text(str("action.reset")) }
            }
        }

        if (com.xeno.CloudSync.config != null) {
            SettingsDivider()
            ToggleRow(
                label = str("cloud.autopush"),
                value = com.xeno.CloudSync.autoPush,
                description = str("cloud.autopush.description"),
                onChange = { com.xeno.CloudSync.updateAutoPush(it) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cloudWorking = str("cloud.working")
                val failedLabel = I18n.get("cloud.failed")
                val push = {
                    if (!busy) scope.launch {
                        busy = true
                        status = cloudWorking
                        try {
                            val n = withContext(Dispatchers.IO) { com.xeno.CloudSync.pushAllSaves() }
                            status = I18n.get("cloud.pushed").format(n)
                        } catch (e: Exception) {
                            status = failedLabel.format(e.message ?: "")
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedButton(
                    onClick = push,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).controllerFocusable("cloud.push", onConfirm = push),
                ) { Text(str("cloud.push")) }
                val pull = {
                    if (!busy) scope.launch {
                        busy = true
                        status = cloudWorking
                        try {
                            val n = withContext(Dispatchers.IO) { com.xeno.CloudSync.pullAllSaves() }
                            status = I18n.get("cloud.pulled").format(n)
                        } catch (e: Exception) {
                            status = failedLabel.format(e.message ?: "")
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedButton(
                    onClick = pull,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).controllerFocusable("cloud.pull", onConfirm = pull),
                ) { Text(str("cloud.pull")) }
            }

            // Cloud game streaming: download a disc under games/ and launch it.
            CloudGameRow(busy = busy) { m ->
                busy = m
            }
        }

        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("✓")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    } // CloudSection Column

    // Second cloud transport: Google Drive save sync. Uses Drive API v3 with
    // appDataFolder (hidden per-app folder). Independent of WebDAV and GitHub.
    Spacer(Modifier.height(12.dp))
    GoogleDriveSection()

    // Third, independent cloud transport: GitHub save sync. It has its own state, its own
    // buttons, and never touches the WebDAV config above; all three can be configured at once.
    Spacer(Modifier.height(12.dp))
    GithubCloudSection()
}

/** Google Drive save sync block: sign-in, auto-push, push/pull. Uses Drive API v3
 *  with appDataFolder (hidden per-app folder). Independent of WebDAV and GitHub. */
@Composable
private fun GoogleDriveSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Read sign-in state once in composable scope.
    val signedIn = com.xeno.GoogleDriveSync.isSignedIn()
    val accountName = com.xeno.GoogleDriveSync.accountName()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsDivider()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📁", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                str("gdrive.section.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            str("gdrive.section.description"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            str("gdrive.section.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )

        // str() is @Composable and the lambdas below run outside composition
        // (onClick handlers / coroutines), so hoist every label here.
        val signInWorkingLabel = str("gdrive.signin.working")
        val signedInLabel = str("gdrive.signedin")
        val signInCancelledLabel = str("gdrive.signin.cancelled")
        val notSignedInLabel = str("gdrive.notsignedin")

        if (!signedIn) {
            val signIn = {
                busy = true
                status = signInWorkingLabel
                // Launch Google Sign-In via ActivityResultLauncher in MainActivityRuntime
                com.xeno.runtime.MainActivityRuntime.launchGoogleSignIn()
                // The result is handled asynchronously via ActivityResultLauncher callback
                // We'll poll for sign-in state change
                scope.launch(Dispatchers.Main) {
                    var attempts = 0
                    while (attempts < 30 && !com.xeno.GoogleDriveSync.isSignedIn()) {
                        delay(500)
                        attempts++
                    }
                    if (com.xeno.GoogleDriveSync.isSignedIn()) {
                        status = signedInLabel.format(com.xeno.GoogleDriveSync.accountName() ?: "")
                    } else {
                        status = signInCancelledLabel
                    }
                    busy = false
                }
                Unit
            }
            OutlinedButton(
                onClick = signIn,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .controllerFocusable("gdrive.signin", onConfirm = signIn),
            ) { Text(str("gdrive.signin")) }
        } else {
            // Signed in — show account and controls
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    signedInLabel.format(accountName ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                val signOut = {
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        com.xeno.GoogleDriveSync.signOut(context)
                        status = notSignedInLabel
                        busy = false
                    }
                    Unit
                }
                OutlinedButton(
                    onClick = signOut,
                    enabled = !busy,
                    modifier = Modifier.controllerFocusable("gdrive.signout", onConfirm = signOut),
                ) { Text(str("gdrive.signout")) }
            }

            SettingsDivider()

            ToggleRow(
                label = str("gdrive.autopush"),
                value = com.xeno.GoogleDriveSync.autoPush,
                description = str("gdrive.autopush.description"),
                onChange = { com.xeno.GoogleDriveSync.updateAutoPush(it) },
            )

            // Hoist labels for coroutine scope
            val pushWorkingLabel = str("gdrive.working")
            val pushFailLabel = str("gdrive.failed")
            val pushedLabel = str("gdrive.pushed")
            val pullWorkingLabel = str("gdrive.working")
            val pullFailLabel = str("gdrive.failed")
            val pulledLabel = str("gdrive.pulled")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val push = {
                    busy = true
                    status = pushWorkingLabel
                    scope.launch(Dispatchers.IO) {
                        try {
                            val n = com.xeno.GoogleDriveSync.pushAllSaves()
                            status = pushedLabel.format(n)
                        } catch (e: Exception) {
                            status = pushFailLabel.format(e.message ?: "")
                        } finally {
                            busy = false
                        }
                    }
                    Unit
                }
                OutlinedButton(
                    onClick = push,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).controllerFocusable("gdrive.push", onConfirm = push),
                ) { Text(str("gdrive.push")) }

                val pull = {
                    busy = true
                    status = pullWorkingLabel
                    scope.launch(Dispatchers.IO) {
                        try {
                            val n = com.xeno.GoogleDriveSync.pullAllSaves()
                            status = pulledLabel.format(n)
                        } catch (e: Exception) {
                            status = pullFailLabel.format(e.message ?: "")
                        } finally {
                            busy = false
                        }
                    }
                    Unit
                }
                OutlinedButton(
                    onClick = pull,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).controllerFocusable("gdrive.pull", onConfirm = pull),
                ) { Text(str("gdrive.pull")) }
            }
        }

        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("✓") || status.contains("Uploaded") || status.contains("Downloaded")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

/** GitHub save sync block: token/repo, verify, auto-push, push/pull. Independent of
 *  WebDAV and Google Drive; the second GitHub transport mirrors [CloudSection]. */
@Composable
private fun GithubCloudSection() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Local mirrors of the volatile backing store so the fields follow edits.
    var token by remember { mutableStateOf(com.xeno.GithubSaveSync.token.orEmpty()) }
    var repo by remember { mutableStateOf(com.xeno.GithubSaveSync.repo) }

    val configured = token.trim().isNotBlank()

    // str() is @Composable — it may NOT be called from the coroutine lambdas below
    // (they run in a non-composable CoroutineScope). Read the labels once here in
    // composable scope for all buttons (save/verify/push/pull); this mirrors the
    // cloud section (hoisted `disabledLabel`/`savedLabel`).
    val ghDisabledLabel = str("github.disabled")
    val ghReadyLabel = str("github.ready")
    val ghVerifyFailLabel = str("github.verify.fail")
    val ghPushWorkingLabel = str("github.push.working")
    val ghPushFailLabel = str("github.push.fail")
    val ghPullWorkingLabel = str("github.pull.working")
    val ghPullFailLabel = str("github.pull.fail")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsDivider()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("↗", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(
                str("github.section.title"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            str("github.section.description"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )

        CloudEditRow(
            controllerId = "github.token",
            label = str("github.token.label"),
            description = str("github.token.description"),
            placeholder = "",
            value = token,
            fieldLabel = str("github.token.field"),
            onChange = { token = it },
        )
        CloudEditRow(
            controllerId = "github.repo",
            label = str("github.repo.label"),
            description = str("github.repo.description"),
            value = repo,
            fieldLabel = str("github.repo.field"),
            onChange = { repo = it },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val save = {
                com.xeno.GithubSaveSync.save(token.trim().takeIf { it.isNotBlank() }, repo)
                if (com.xeno.GithubSaveSync.token == null) token = ""
                repo = com.xeno.GithubSaveSync.repo
                status = if (com.xeno.GithubSaveSync.token == null) ghDisabledLabel else ghReadyLabel
            }
            OutlinedButton(
                onClick = save,
                modifier = Modifier.weight(1f).controllerFocusable("github.save", onConfirm = save),
            ) { Text(str("github.save")) }
            val verify = {
                if (!busy) scope.launch {
                    busy = true
                    status = ghVerifyFailLabel
                    try {
                        val who = withContext(Dispatchers.IO) { com.xeno.GithubSaveSync.verify() }
                        status = if (who != null) I18n.get("github.verify.ok").format(who) else ghVerifyFailLabel
                    } catch (e: Exception) {
                        status = ghVerifyFailLabel
                    } finally {
                        busy = false
                    }
                }
            }
            OutlinedButton(
                onClick = verify,
                enabled = !busy,
                modifier = Modifier.weight(1f).controllerFocusable("github.verify", onConfirm = verify),
            ) { Text(str("github.verify")) }
        }

        if (configured) {
            ToggleRow(
                label = str("github.autopush"),
                value = com.xeno.GithubSaveSync.autoPush,
                onChange = { com.xeno.GithubSaveSync.setAutoPush(it) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val push = {
                    if (!busy) scope.launch {
                        busy = true
                        status = ghPushWorkingLabel
                        try {
                            val n = withContext(Dispatchers.IO) { com.xeno.GithubSaveSync.pushAll() }
                            status = if (n > 0) I18n.get("github.push.ok").format(n) else ghPushFailLabel
                        } catch (e: Exception) {
                            status = ghPushFailLabel
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedButton(
                    onClick = push,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).controllerFocusable("github.push", onConfirm = push),
                ) { Text(str("github.push")) }
                val pull = {
                    if (!busy) scope.launch {
                        busy = true
                        status = ghPullWorkingLabel
                        try {
                            val n = withContext(Dispatchers.IO) { com.xeno.GithubSaveSync.pullAll() }
                            status = if (n > 0) I18n.get("github.pull.ok").format(n) else ghPullFailLabel
                        } catch (e: Exception) {
                            status = ghPullFailLabel
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedButton(
                    onClick = pull,
                    enabled = !busy,
                    modifier = Modifier.weight(1f).controllerFocusable("github.pull", onConfirm = pull),
                ) { Text(str("github.pull")) }
            }
        }

        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.startsWith("✓")) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

/** A name entry row mirroring [LocalLinkRow], but text-only and custom-labelled
 *  (LocalLinkRow carries a Generate action and the shared rows hardcode "Address"). */
@Composable
private fun CloudEditRow(
    controllerId: String,
    label: String,
    description: String,
    value: String,
    fieldLabel: String,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    val edit = {
        com.xeno.ui.home.LibraryKeyboard.open(value, onChange, fieldLabel)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .clickable(onClick = edit)
            .controllerFocusable(controllerId, onConfirm = edit)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value.ifEmpty { placeholder.ifEmpty { "\u2014" } },
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Download-and-play a single game file from the cloud ("cloud games"). */
@Composable
private fun CloudGameRow(busy: Boolean, setBusy: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            str("cloud.games.title"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            str("cloud.games.description"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CloudEditRow(
            controllerId = "cloud.game",
            label = str("cloud.games.file"),
            description = str("cloud.games.file.description"),
            value = name,
            fieldLabel = str("cloud.games.file.fieldLabel"),
            placeholder = "BLUS30475/BLUS30475.iso",
            onChange = { name = it },
        )
        if (name.isNotBlank()) {
            val dlWorking = str("cloud.download.working")
            val dlDone = str("cloud.download.done")
            val dlFailed = str("cloud.download.failed")
            val launch = {
                if (!busy) scope.launch {
                    setBusy(true)
                    status = dlWorking
                    try {
                        val local = withContext(Dispatchers.IO) { com.xeno.CloudSync.downloadGame(name) }
                        if (local != null) {
                            status = dlDone
                            com.xeno.runtime.MainActivityRuntime.launchGame(local)
                        } else {
                            status = dlFailed
                        }
                    } catch (e: Exception) {
                        status = dlFailed
                    } finally {
                        setBusy(false)
                    }
                }
            }
            val streamWorking = str("cloud.games.stream.working")
            val streamDone = str("cloud.games.stream.done")
            val streamFailed = str("cloud.games.stream.failed")
            val stream = {
                if (!busy) scope.launch {
                    setBusy(true)
                    status = streamWorking
                    try {
                        val url = withContext(Dispatchers.IO) { com.xeno.CloudSync.streamGameUrl(name) }
                        if (url != null) {
                            status = streamDone
                            com.xeno.runtime.MainActivityRuntime.launchGame(url)
                        } else {
                            status = streamFailed
                        }
                    } catch (e: Exception) {
                        status = streamFailed
                    } finally {
                        setBusy(false)
                    }
                }
            }
            val context = LocalContext.current
            val installWorking = str("cloud.games.install.working")
            val installDone = str("cloud.games.install.done")
            val installFailed = str("cloud.games.install.failed")
            val install = {
                if (!busy) scope.launch {
                    setBusy(true)
                    status = installWorking
                    try {
                        val url = withContext(Dispatchers.IO) { com.xeno.CloudSync.streamGameUrl(name) }
                        if (url != null) {
                            val id = ProgressRepository.create(context, installWorking)
                            val ok = withContext(Dispatchers.IO) { RPCSX.instance.installPkgFromUrl(url, id) }
                            if (ok) {
                                withContext(Dispatchers.IO) {
                                    com.xeno.data.library.GameLibraryRepository(context).invalidateCache()
                                }
                                status = installDone
                            } else {
                                status = installFailed
                            }
                        } else {
                            status = installFailed
                        }
                    } catch (e: Exception) {
                        status = installFailed
                    } finally {
                        setBusy(false)
                    }
                }
            }
            OutlinedButton(
                onClick = launch,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().controllerFocusable("cloud.game.launch", onConfirm = launch),
            ) { Text(str("cloud.games.launch")) }
            OutlinedButton(
                onClick = stream,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().controllerFocusable("cloud.game.stream", onConfirm = stream),
            ) { Text(str("cloud.games.stream")) }
            OutlinedButton(
                onClick = install,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().controllerFocusable("cloud.game.install", onConfirm = install),
            ) { Text(str("cloud.games.install")) }
            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** This device's own LAN IPv4 addresses, so a host can read one out to the guests instead of being
 *  told to go hunting in Android's settings. Hotspot interfaces are included on purpose — a hotspot
 *  is the most reliable way to get two handhelds onto one network. */
private fun enumerateLocalIPv4(): List<String> {
    val out = linkedSetOf<String>()
    runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
        for (iface in interfaces.toList()) {
            val usable = runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
            if (!usable) continue
            for (addr in iface.inetAddresses.toList()) {
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress)
                    addr.hostAddress?.let { out.add(it) }
            }
        }
    }
    return out.toList()
}

/** A fresh 8-character room code. Seeded automatically when LAN mode is first selected, because an
 *  empty code silently disables DEV9 (see the Network mode onChange for the full failure chain).
 *  Uppercase alphanumerics only, matching what the native side normalises to. */
private fun generateRoomCode(): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I/O/0/1 — these get read aloud
    return (1..8).map { alphabet[kotlin.random.Random.nextInt(alphabet.length)] }.joinToString("")
}

/** A stable guest peer id in 2..65533, derived from ANDROID_ID so it differs per device and never
 *  needs to be chosen by hand. 1 is reserved for the host by the wire protocol. */
private fun derivePeerId(context: android.content.Context): Int {
    val seed = runCatching {
        android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID,
        )
    }.getOrNull().orEmpty().ifEmpty { android.os.Build.FINGERPRINT }
    // 65532 slots starting at 2; abs() on the hash, guarding Int.MIN_VALUE.
    val h = seed.hashCode()
    val positive = if (h == Int.MIN_VALUE) 0 else if (h < 0) -h else h
    return 2 + (positive % 65532)
}

/** A tappable label+subtitle row that fires an Intent. Used for the Wi-Fi shortcut (the devices have
 *  to be on one network before any of this works, and that is the step people miss) and for the
 *  supported-games list. Registers with the pad-nav registry — without that the whole Local Link
 *  section was unreachable on a controller, since only the shared ToggleRow/SegmentedRow widgets
 *  self-register and every custom row here was skipped. */
@Composable
private fun ActionRow(
    controllerId: String,
    label: String,
    description: String,
    context: android.content.Context,
    intent: () -> android.content.Intent,
) {
    val fire = {
        // runCatching: no ACTION_VIEW handler (no browser) or a blocked settings intent must not
        // take the settings screen down with it.
        runCatching {
            context.startActivity(intent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Unit
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .clickable(onClick = fire)
            .controllerFocusable(controllerId, onConfirm = fire)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

/** Wikipedia's LAN-games section: the authoritative answer to "which games can I actually use this
 *  with?", which is the first thing anyone asks. Kept as a link rather than a baked-in list so it
 *  can't go stale in our strings. */
private const val LAN_GAMES_URL =
    "https://en.wikipedia.org/wiki/List_of_PlayStation_2_online_games#LAN_Games"

/** A non-editable value row (host address, local peer id) — same shape as the editable rows so the
 *  section reads consistently, but with no tap target, because these are computed, not chosen. */
@Composable
private fun ReadOnlyRow(label: String, value: String, description: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value,
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * A Local Link field: same look as [EditableTextRow], but it shows a description under the label
 * and does not assume the value is an IP address. [EditableTextRow] prefills AND displays
 * "0.0.0.0" for an empty value and hardcodes the edit dialog's field label to "Address" — correct
 * for the DNS/gateway rows it serves, wrong for a room code, a port or a peer id. Kept separate
 * rather than adding switches to that one, which has a dozen existing call sites.
 *
 * onChange receives the trimmed text; callers do their own validation/coercion.
 */
@Composable
private fun LocalLinkRow(
    controllerId: String,
    label: String,
    value: String,
    description: String,
    fieldLabel: String,
    /** When supplied, adds a Generate action (button + D-pad Right) that fills the field. Used for
     *  the room code, which has validity rules a person shouldn't have to remember. */
    onGenerate: (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    // Text entry goes through LibraryKeyboard, NOT an AlertDialog. A Compose dialog takes its own
    // focused window and swallows gamepad keys, so a pad user could open it and then be stuck with
    // no way to type or dismiss. LibraryKeyboard is D-pad navigable by design, and it also honours
    // the "Use system keyboard" preference for touch users.
    val edit = {
        com.xeno.ui.home.LibraryKeyboard.open(value, onChange, fieldLabel)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .clickable(onClick = edit)
            .controllerFocusable(
                controllerId,
                onConfirm = edit,
                // D-pad Right regenerates, matching how other rows use left/right to adjust.
                onRight = onGenerate,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value.ifEmpty { str("network.localLink.notSet") },
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onGenerate != null) {
                // One-tap valid code, without opening the editor. Its own clickable consumes the
                // tap, so it does not also open the row's edit dialog.
                TextButton(onClick = onGenerate) { Text(str("network.localLink.generate")) }
            }
        }
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun EditableTextRow(
    controllerId: String,
    label: String,
    value: String,
    description: String,
    /** Shown greyed when the value is empty; also what the editor starts from. */
    placeholder: String = "",
    // Last, so callers can pass it as a trailing lambda like every other row here.
    onChange: (String) -> Unit,
) {
    var editing by remember(label) { mutableStateOf(false) }
    var draft by remember(label, value) { mutableStateOf(value.ifEmpty { placeholder }) }
    val open = { draft = value.ifEmpty { placeholder }; editing = true }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(str("net.address")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(draft.trim())
                    editing = false
                }) { Text(str("action.save")) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(str("action.cancel")) }
            },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .clickable(onClick = open)
            // Without this the row is invisible to a controller: only the shared
            // ToggleRow/SegmentedRow widgets self-register with the pad-nav registry.
            .controllerFocusable(controllerId, onConfirm = open)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                value.ifEmpty { placeholder.ifEmpty { "\u2014" } },
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun DeviceChooser(
    selected: String,
    adapters: List<String>,
    onChange: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(str("network.ethernetDevice"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        adapters.forEach { adapter ->
            val active = adapter == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { onChange(adapter) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    adapter,
                    color = if (active) Colors.pasx2_blue else Color(0xFFCCCCCC),
                    fontSize = 15.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (active) {
                    Text(str("network.selected"), color = Colors.pasx2_blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HddFileRow(fileName: String, onChange: (String) -> Unit, onReset: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(fileName) { mutableStateOf(fileName) }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(str("network.hddImage.title")) },
            text = {
                Column {
                    Text(
                        str("network.hddImage.dialogHint"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        label = { Text(str("network.hddImage.fieldLabel")) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(draft.trim())
                    editing = false
                }) { Text(str("action.save")) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(str("action.cancel")) }
            },
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rowAura())
            .clickable { draft = fileName; editing = true }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(str("network.hddImage.title"), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(fileName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                str("action.reset"),
                color = Colors.pasx2_blue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onReset() }.padding(start = 8.dp),
            )
        }
    }
}

private fun enumerateAdapters(): List<String> {
    val out = linkedSetOf("Auto")
    runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
        interfaces.toList()
            .filter { iface ->
                runCatching {
                    iface.isUp && !iface.isLoopback && !iface.isVirtual
                }.getOrDefault(false)
            }
            .mapTo(out) { it.name }
    }
    return out.toList()
}
