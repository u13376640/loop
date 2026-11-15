package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.LoopRepository
import com.example.ui.components.LoopCircleLogo
import com.example.ui.viewmodel.LoopViewModel
import com.example.util.CookieExportResult
import com.example.util.CookieFormat
import com.example.util.CookieImportMode
import com.example.util.CookieTransferManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LoopViewModel,
    onNavigateToMain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val activeUrl by viewModel.activeUrl.collectAsStateWithLifecycle()
    val isJsEnabled by viewModel.isJsEnabled.collectAsStateWithLifecycle()
    val isDesktopMode by viewModel.isDesktopMode.collectAsStateWithLifecycle()
    val hasPassword by viewModel.hasPassword.collectAsStateWithLifecycle()

    var urlInput by remember(activeUrl) { mutableStateOf(activeUrl) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    // Access Code Protection for Main Page URL ("dcsus7")
    var isUrlCardUnlocked by remember { mutableStateOf(false) }
    var showAccessCodeDialog by remember { mutableStateOf(false) }
    var accessCodeInput by remember { mutableStateOf("") }
    var accessCodeError by remember { mutableStateOf(false) }

    // Password Dialog States
    var showCreatePasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showRemovePasswordDialog by remember { mutableStateOf(false) }

    // Cookie Export & Import States
    var showExportCookiesDialog by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(CookieFormat.JSON) }
    var isExportingCookies by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<CookieExportResult?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    var showImportCookiesDialog by remember { mutableStateOf(false) }
    var importRawText by remember { mutableStateOf("") }
    var selectedImportMode by remember { mutableStateOf<CookieImportMode?>(null) }
    var isImportingCookies by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    // Zero-permission Storage Access Framework launchers for cookie files
    val handleExportUri: (Uri?) -> Unit = { uri ->
        val payload = exportResult?.serializedText
        val cookieCount = exportResult?.cookies?.size ?: 0
        if (uri != null && !payload.isNullOrEmpty()) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(payload.toByteArray(Charsets.UTF_8))
                            stream.flush()
                        } ?: throw IllegalStateException("Unable to open destination file")
                    }
                    viewModel.showSnackbar("Saved $cookieCount cookies to file")
                    showExportCookiesDialog = false
                } catch (e: Exception) {
                    exportError = e.localizedMessage ?: "Failed to save cookie file"
                }
            }
        }
    }

    val createJsonCookieFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = handleExportUri
    )

    val createTxtCookieFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
        onResult = handleExportUri
    )

    val openCookieFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val fileContent = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        } ?: throw IllegalStateException("Unable to read selected file")
                    }
                    importRawText = fileContent
                    importError = null
                } catch (e: Exception) {
                    importError = e.localizedMessage ?: "Failed to read cookie file"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            modifier = Modifier.testTag("settings_top_app_bar"),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoopCircleLogo(size = 28.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (isUrlCardUnlocked) {
                            isUrlCardUnlocked = false
                        } else {
                            accessCodeInput = ""
                            accessCodeError = false
                            showAccessCodeDialog = true
                        }
                    },
                    modifier = Modifier.testTag("secret_url_lock_icon")
                ) {
                    Icon(
                        imageVector = if (isUrlCardUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = if (isUrlCardUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Section 1: Main Page URL Configuration (Protected by access code "dcsus7")
            if (isUrlCardUnlocked) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("active_url_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Main Page URL",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { isUrlCardUnlocked = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock URL Settings",
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Set any custom URL to display on the Main Page. Default is https://dcsplayer.blogspot.com/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // URL Input Field
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("url_input_field"),
                                label = { Text("Website URL") },
                                placeholder = { Text("https://dcsplayer.blogspot.com/") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        if (urlInput.isNotBlank()) {
                                            viewModel.setActiveUrl(urlInput)
                                        }
                                    }
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    Row {
                                        if (urlInput.isNotEmpty()) {
                                            IconButton(onClick = { urlInput = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear URL"
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                clipboardManager.getText()?.text?.let { clipboardText ->
                                                    urlInput = clipboardText
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste from clipboard"
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        if (urlInput.isNotBlank()) {
                                            viewModel.setActiveUrl(urlInput)
                                            onNavigateToMain()
                                        } else {
                                            Toast.makeText(context, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("apply_and_show_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Launch,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Apply & Show", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.resetToDefault()
                                        urlInput = LoopRepository.DEFAULT_URL
                                    },
                                    modifier = Modifier.testTag("reset_default_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Default")
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: App Password
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_settings_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (hasPassword) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (hasPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Password",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (hasPassword) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = if (hasPassword) "Active" else "Empty (None)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (hasPassword) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (hasPassword) {
                                "Loop is protected with a password. You must enter this password when launching the app."
                            } else {
                                "Default is empty (no password). You can create an app password below to secure access."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (!hasPassword) {
                            // Option to create password
                            Button(
                                onClick = { showCreatePasswordDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_password_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Password,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create App Password")
                            }
                        } else {
                            // Options when password is active
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showChangePasswordDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("change_password_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockReset,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Change")
                                }

                                OutlinedButton(
                                    onClick = { showRemovePasswordDialog = true },
                                    modifier = Modifier.testTag("remove_password_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { viewModel.lockApp() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("lock_now_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock App Now")
                            }
                        }
                    }
                }
            }

            // Section 3: AdBlocker
            item {
                val isAdBlockEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()
                val blockedCount by viewModel.blockedAdsCount.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adblocker_settings_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AdBlocker",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAdBlockEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = if (isAdBlockEnabled) "Protected" else "Off",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isAdBlockEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Intercepts ad networks, popup scripts, and banner elements so websites and video players load cleanly without interruptions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // AdBlocker Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Block Ads & Popups",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$blockedCount ads & trackers blocked",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (blockedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isAdBlockEnabled,
                                onCheckedChange = { viewModel.toggleAdBlock(it) },
                                modifier = Modifier.testTag("adblock_switch")
                            )
                        }

                        if (blockedCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { viewModel.resetBlockedAdsCount() },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Reset Counter", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Section 4: Browser Engine Preferences
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Engine Preferences",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // JavaScript Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable JavaScript",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Required for interactive web apps and dynamic player content.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isJsEnabled,
                                onCheckedChange = { viewModel.toggleJs(it) },
                                modifier = Modifier.testTag("js_switch")
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Desktop Mode Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Desktop View Mode",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Request desktop website layout instead of mobile view.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isDesktopMode,
                                onCheckedChange = { viewModel.toggleDesktopMode(it) },
                                modifier = Modifier.testTag("desktop_mode_switch")
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Browser Cookie Export & Import
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Browser Cookies & Sessions",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Export or import signed-in accounts (e.g., Google) via JSON or cookies.txt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        exportError = null
                                        exportResult = null
                                        showExportCookiesDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("export_cookies_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = "Export Cookies",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = {
                                        importRawText = ""
                                        selectedImportMode = null
                                        importError = null
                                        isImportingCookies = false
                                        showImportCookiesDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("import_cookies_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Import Cookies",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // Clear Cache
                        OutlinedButton(
                            onClick = { showClearCacheDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("clear_cache_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Clear Cache & Cookies")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Dialog 0: Access Code Dialog for Main Page URL ("dcsus7")
    if (showAccessCodeDialog) {
        AlertDialog(
            onDismissRequest = { showAccessCodeDialog = false },
            title = { Text("Access Code Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the authorized access code to configure the Main Page URL:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = accessCodeInput,
                        onValueChange = {
                            accessCodeInput = it
                            accessCodeError = false
                        },
                        label = { Text("Access Code") },
                        singleLine = true,
                        isError = accessCodeError,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("access_code_input")
                    )

                    AnimatedVisibility(visible = accessCodeError) {
                        Text(
                            text = "Incorrect access code. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (accessCodeInput.trim() == "dcsus7") {
                            isUrlCardUnlocked = true
                            showAccessCodeDialog = false
                            Toast.makeText(context, "Access code verified", Toast.LENGTH_SHORT).show()
                        } else {
                            accessCodeError = true
                        }
                    },
                    modifier = Modifier.testTag("verify_access_code_button")
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessCodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 1: Create Password Dialog
    if (showCreatePasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var passVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreatePasswordDialog = false },
            title = { Text("Create App Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a password to protect your Loop app. You will need to enter this password whenever the app opens.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = {
                            newPass = it
                            errorMsg = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(
                                    imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = {
                            confirmPass = it
                            errorMsg = null
                        },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = errorMsg != null) {
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.isBlank()) {
                            errorMsg = "Password cannot be empty"
                        } else if (newPass != confirmPass) {
                            errorMsg = "Passwords do not match"
                        } else {
                            viewModel.setAppPassword(newPass) {
                                showCreatePasswordDialog = false
                            }
                        }
                    }
                ) {
                    Text("Save Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 2: Change Password Dialog
    if (showChangePasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var passVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change App Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = {
                            currentPass = it
                            errorMsg = null
                        },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPass,
                        onValueChange = {
                            newPass = it
                            errorMsg = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = {
                            confirmPass = it
                            errorMsg = null
                        },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = errorMsg != null) {
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPass.isBlank()) {
                            errorMsg = "New password cannot be empty"
                        } else if (newPass != confirmPass) {
                            errorMsg = "New passwords do not match"
                        } else {
                            viewModel.changeAppPassword(
                                currentPass = currentPass,
                                newPass = newPass,
                                onSuccess = {
                                    showChangePasswordDialog = false
                                },
                                onError = {
                                    errorMsg = "Current password incorrect"
                                }
                            )
                        }
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 3: Remove Password Dialog
    if (showRemovePasswordDialog) {
        var currentPass by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var passVisible by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRemovePasswordDialog = false },
            title = { Text("Remove App Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your current password to disable password protection.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = currentPass,
                        onValueChange = {
                            currentPass = it
                            errorMsg = null
                        },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    AnimatedVisibility(visible = errorMsg != null) {
                        Text(
                            text = errorMsg ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeAppPassword(
                            currentPass = currentPass,
                            onSuccess = {
                                showRemovePasswordDialog = false
                            },
                            onError = {
                                errorMsg = "Incorrect password"
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove Protection")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 4: Clear Cache Dialog
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache & Cookies") },
            text = { Text("This will clear all cached web pages, cookies, and local web storage. Do you wish to continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCacheAndCookies()
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 5: Export Browser Cookies
    if (showExportCookiesDialog) {
        LaunchedEffect(showExportCookiesDialog, exportFormat) {
            isExportingCookies = true
            exportError = null
            viewModel.exportCookies(
                format = exportFormat,
                onReady = { result ->
                    exportResult = result
                    isExportingCookies = false
                },
                onError = { err ->
                    exportError = err
                    isExportingCookies = false
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showExportCookiesDialog = false },
            title = { Text("Export Browser Cookies") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Choose export format:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exportFormat == CookieFormat.JSON,
                            onClick = { exportFormat = CookieFormat.JSON },
                            label = { Text("JSON") },
                            modifier = Modifier.testTag("export_format_json")
                        )
                        FilterChip(
                            selected = exportFormat == CookieFormat.NETSCAPE,
                            onClick = { exportFormat = CookieFormat.NETSCAPE },
                            label = { Text("cookies.txt") },
                            modifier = Modifier.testTag("export_format_netscape")
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Security notice: Exported cookies contain active login sessions. Keep files and copied text private.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    when {
                        isExportingCookies -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Reading browser cookies...", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        exportError != null -> {
                            Text(
                                text = exportError ?: "Export error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        exportResult != null -> {
                            val result = exportResult!!
                            if (result.cookies.isEmpty()) {
                                Text(
                                    text = "No cookies found yet. Browse or sign into a website on the Main Page first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "${result.cookies.size} cookies across ${result.domainCount} domains",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                val previewSnippet = remember(result.serializedText) {
                                    if (result.serializedText.length > 2500) {
                                        result.serializedText.take(2500) + "\n... (truncated preview)"
                                    } else {
                                        result.serializedText
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp, max = 140.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .verticalScroll(rememberScrollState())
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = previewSnippet,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isExportingCookies && exportResult != null && exportResult!!.cookies.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val result = exportResult ?: return@OutlinedButton
                                    clipboardManager.setText(AnnotatedString(result.serializedText))
                                    viewModel.showSnackbar(
                                        "Copied ${result.cookies.size} cookies (${exportFormat.label})"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_copy_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Cookies",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    when (exportFormat) {
                                        CookieFormat.JSON -> createJsonCookieFileLauncher.launch("loop-cookies.json")
                                        CookieFormat.NETSCAPE -> createTxtCookieFileLauncher.launch("loop-cookies.txt")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_save_file_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Save Cookie File",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save File", maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportCookiesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Dialog 6: Import Browser Cookies (asks Merge vs Replace every time)
    if (showImportCookiesDialog) {
        val importPreview = remember(importRawText) {
            CookieTransferManager.parseCookies(importRawText)
        }

        AlertDialog(
            onDismissRequest = { if (!isImportingCookies) showImportCookiesDialog = false },
            title = { Text("Import Browser Cookies") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                openCookieFileLauncher.launch(
                                    arrayOf("application/json", "text/plain", "*/*")
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("import_choose_file_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Choose Cookie File",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose File", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    importRawText = clipText
                                    importError = null
                                } else {
                                    importError = "Clipboard is empty"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("import_paste_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste Cookie Data",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste", maxLines = 1)
                        }
                    }

                    OutlinedTextField(
                        value = importRawText,
                        onValueChange = {
                            importRawText = it
                            importError = null
                        },
                        label = { Text("JSON or cookies.txt") },
                        placeholder = { Text("Paste exported JSON array or Netscape cookies.txt...") },
                        minLines = 3,
                        maxLines = 5,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        trailingIcon = {
                            if (importRawText.isNotEmpty()) {
                                IconButton(onClick = {
                                    importRawText = ""
                                    importError = null
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear Cookie Text"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_cookies_input")
                    )

                    if (importRawText.isNotBlank()) {
                        if (importPreview.error != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = importPreview.error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else if (importPreview.cookies.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Detected ${importPreview.detectedFormat?.label ?: "cookies"} • ${importPreview.cookies.size} cookies across ${importPreview.domainCount} domains",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "How should existing sessions be handled?",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Merge option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedImportMode == CookieImportMode.MERGE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        color = if (selectedImportMode == CookieImportMode.MERGE) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { selectedImportMode = CookieImportMode.MERGE }
                            .testTag("import_mode_merge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedImportMode == CookieImportMode.MERGE,
                                onClick = { selectedImportMode = CookieImportMode.MERGE }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Merge with existing",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Keep current sessions and add/update imported cookies.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Replace option
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedImportMode == CookieImportMode.REPLACE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        color = if (selectedImportMode == CookieImportMode.REPLACE) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { selectedImportMode = CookieImportMode.REPLACE }
                            .testTag("import_mode_replace")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedImportMode == CookieImportMode.REPLACE,
                                onClick = { selectedImportMode = CookieImportMode.REPLACE }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replace all existing",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Clear all current browser cookies before importing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (importError != null) {
                        Text(
                            text = importError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                val chosenMode = selectedImportMode
                Button(
                    onClick = {
                        if (chosenMode == null) return@Button
                        isImportingCookies = true
                        importError = null
                        viewModel.importCookies(
                            rawText = importRawText,
                            mode = chosenMode,
                            onSuccess = {
                                isImportingCookies = false
                                showImportCookiesDialog = false
                            },
                            onError = { err ->
                                isImportingCookies = false
                                importError = err
                            }
                        )
                    },
                    enabled = importPreview.error == null &&
                        importPreview.cookies.isNotEmpty() &&
                        chosenMode != null &&
                        !isImportingCookies,
                    modifier = Modifier.testTag("confirm_import_cookies_button")
                ) {
                    if (isImportingCookies) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Import")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showImportCookiesDialog = false },
                    enabled = !isImportingCookies
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
