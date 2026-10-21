package com.cleanurl.app

import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cleanurl.app.core.ClipboardHelper
import com.cleanurl.app.core.CleanResult
import com.cleanurl.app.core.LinkProcessor
import com.cleanurl.app.core.Platform
import com.cleanurl.app.data.AppPreferences
import com.cleanurl.app.data.HistoryEntry
import com.cleanurl.app.data.Settings
import com.cleanurl.app.ui.theme.CleanUrlTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val prefs by lazy { AppPreferences.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CleanUrlTheme {
                MainScreen(prefs)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The share receiver may have added history entries while we were in the background.
        prefs.reload()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(prefs: AppPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val processor = remember { LinkProcessor() }
    val settings by prefs.settings.collectAsState()
    val history by prefs.history.collectAsState()

    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CleanResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    val copiedMessage = stringResource(R.string.toast_copied)
    val noUrlMessage = stringResource(R.string.toast_no_url)
    val clipboardEmptyMessage = stringResource(R.string.toast_clipboard_empty)
    val copiedShortMessage = stringResource(R.string.toast_copied_history)

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    fun cleanAndCopy(text: String) {
        busy = true
        scope.launch {
            val cleaned = processor.process(text, settings.toCleanOptions())
            busy = false
            if (cleaned == null) {
                toast(noUrlMessage)
                return@launch
            }
            result = cleaned
            ClipboardHelper.write(context, cleaned.cleaned)
            prefs.addHistory(HistoryEntry(cleaned.platform, cleaned.cleaned, System.currentTimeMillis()))
            ClipboardHelper.notifyCopied(context, copiedMessage, settings.toastOnAndroid13)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ClipboardCard(busy = busy) {
                    val text = ClipboardHelper.read(context)
                    if (text.isNullOrBlank()) toast(clipboardEmptyMessage) else cleanAndCopy(text)
                }
            }

            result?.let { item { ResultCard(it) { ClipboardHelper.write(context, it.cleaned); toast(copiedShortMessage) } } }

            item {
                ManualInputCard(
                    input = input,
                    busy = busy,
                    onInputChange = { input = it },
                    onClean = { cleanAndCopy(input) },
                )
            }

            item {
                SettingsCard(settings = settings, onChange = prefs::updateSettings)
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (history.isNotEmpty()) {
                        TextButton(onClick = prefs::clearHistory) { Text(stringResource(R.string.history_clear)) }
                    }
                }
            }

            if (history.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(history, key = { it.timestamp.toString() + it.url }) { entry ->
                    HistoryRow(entry) {
                        ClipboardHelper.write(context, entry.url)
                        toast(copiedShortMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardCard(busy: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.clean_clipboard_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.clean_clipboard_desc), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    stringResource(if (busy) R.string.processing else R.string.clean_clipboard_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun ResultCard(result: CleanResult, onCopy: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.result_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                PlatformChip(result.platform)
            }
            Spacer(Modifier.height(8.dp))
            if (result.changed) {
                Text(
                    result.original,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("↓", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    stringResource(R.string.result_unchanged),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(result.cleaned, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onCopy, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.result_copy))
            }
        }
    }
}

@Composable
private fun ManualInputCard(
    input: String,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onClean: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text(stringResource(R.string.manual_hint)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onClean,
                enabled = input.isNotBlank() && !busy,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.manual_button))
            }
        }
    }
}

@Composable
private fun SettingsCard(settings: Settings, onChange: ((Settings) -> Settings) -> Unit) {
    val isAndroid13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    Card {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingRow(
                title = stringResource(R.string.setting_expand_fb),
                description = stringResource(R.string.setting_expand_fb_desc),
                checked = settings.expandShortLinks,
                onCheckedChange = { value -> onChange { it.copy(expandShortLinks = value) } },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingRow(
                title = stringResource(R.string.setting_shorts),
                description = stringResource(R.string.setting_shorts_desc),
                checked = settings.convertShorts,
                onCheckedChange = { value -> onChange { it.copy(convertShorts = value) } },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            SettingRow(
                title = stringResource(R.string.setting_toast),
                description = stringResource(
                    if (isAndroid13) R.string.setting_toast_desc else R.string.setting_toast_desc_old,
                ),
                checked = !isAndroid13 || settings.toastOnAndroid13,
                enabled = isAndroid13,
                onCheckedChange = { value -> onChange { it.copy(toastOnAndroid13 = value) } },
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformChip(entry.platform)
                Spacer(Modifier.width(8.dp))
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.timestamp).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                entry.url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlatformChip(platform: Platform) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            platform.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
