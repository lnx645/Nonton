@file:OptIn(coil.annotation.ExperimentalCoilApi::class)

package id.nonton.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import id.nonton.app.BuildConfig
import id.nonton.app.R
import id.nonton.app.core.ServiceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val pipEnabled by viewModel.pipEnabled.collectAsStateWithLifecycle()
    val serviceId by viewModel.selectedServiceId.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_appearance)) {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = themeLabel(themeMode),
                    onClick = { dialog = "theme" },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.Tune,
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = "Material You (Android 12+)",
                    trailing = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor,
                        )
                    },
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_playback)) {
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = stringResource(R.string.settings_default_service),
                    subtitle = ServiceCatalog.nameFor(serviceId),
                    onClick = { dialog = "service" },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.settings_default_quality),
                    subtitle = qualityLabel(quality),
                    onClick = { dialog = "quality" },
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.settings_pip),
                    subtitle = stringResource(R.string.settings_pip_desc),
                    trailing = {
                        Switch(
                            checked = pipEnabled,
                            onCheckedChange = viewModel::setPip,
                        )
                    },
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_data)) {
                SettingsRow(
                    icon = Icons.Outlined.Storage,
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = "Thumbnail dan gambar",
                    onClick = {
                        context.imageLoader.memoryCache?.clear()
                        scope.launch(Dispatchers.IO) {
                            context.imageLoader.diskCache?.clear()
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_cache_cleared),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_about)) {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.app_name),
                    subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                )
                SettingsDivider()
                Text(
                    text = stringResource(R.string.settings_about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Outlined.Cached,
                    title = stringResource(R.string.settings_license),
                    subtitle = "GNU General Public License v3.0",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"),
                            )
                        )
                    },
                )
            }
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                "system" to stringResource(R.string.settings_theme_system),
                "light" to stringResource(R.string.settings_theme_light),
                "dark" to stringResource(R.string.settings_theme_dark),
            ),
            selected = themeMode,
            onSelect = {
                viewModel.setTheme(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "service" -> ChoiceDialog(
            title = stringResource(R.string.settings_default_service),
            options = ServiceCatalog.all.map { it.id.toString() to it.name },
            selected = serviceId.toString(),
            onSelect = {
                viewModel.setService(it.toInt())
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "quality" -> ChoiceDialog(
            title = stringResource(R.string.settings_default_quality),
            options = listOf(
                "best" to stringResource(R.string.settings_quality_best),
                "720" to "720p",
                "480" to "480p",
                "360" to "360p",
            ),
            selected = quality,
            onSelect = {
                viewModel.setQuality(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 52.dp),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = { onSelect(value) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel))
            }
        },
    )
}

private fun themeLabel(mode: String): String = when (mode) {
    "light" -> "Terang"
    "dark" -> "Gelap"
    else -> "Ikuti sistem"
}

private fun qualityLabel(value: String): String = when (value) {
    "720" -> "720p"
    "480" -> "480p"
    "360" -> "360p"
    else -> "Terbaik"
}
