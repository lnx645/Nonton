package id.nonton.app.ui.settings

import androidx.lifecycle.ViewModel
import id.nonton.app.core.SettingsStore
import id.nonton.app.ui.simpleViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {

    val themeMode: StateFlow<String> = settings.themeMode
    val dynamicColor: StateFlow<Boolean> = settings.dynamicColor
    val pipEnabled: StateFlow<Boolean> = settings.pipEnabled
    val selectedServiceId: StateFlow<Int> = settings.selectedServiceId

    private val _quality = MutableStateFlow(settings.defaultQuality)
    val quality: StateFlow<String> = _quality.asStateFlow()

    fun setTheme(mode: String) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setPip(enabled: Boolean) = settings.setPipEnabled(enabled)
    fun setService(id: Int) = settings.setSelectedService(id)

    fun setQuality(value: String) {
        settings.defaultQuality = value
        _quality.value = value
    }

    companion object {
        fun factory(settings: SettingsStore) = simpleViewModelFactory { SettingsViewModel(settings) }
    }
}
