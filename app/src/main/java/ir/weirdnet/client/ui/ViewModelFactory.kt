package ir.weirdnet.client.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import ir.weirdnet.client.WeirdNetApplication

@Composable
fun weirdNetViewModelFactory(): WeirdNetViewModelFactory {
    val context = LocalContext.current
    return WeirdNetViewModelFactory(WeirdNetApplication.from(context))
}


/**
 * Minimal manual ViewModel factory. WEIRDNET intentionally has no dependency-
 * injection framework: at this app's scale a small factory like this is easier
 * for a newcomer to read and modify than a Hilt/Dagger graph.
 */
class WeirdNetViewModelFactory(
    private val app: WeirdNetApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(ir.weirdnet.client.ui.home.HomeViewModel::class.java) ->
                ir.weirdnet.client.ui.home.HomeViewModel(app.profileRepository, app.settingsRepository) as T
            modelClass.isAssignableFrom(ir.weirdnet.client.ui.profiles.ProfilesViewModel::class.java) ->
                ir.weirdnet.client.ui.profiles.ProfilesViewModel(app.profileRepository) as T
            modelClass.isAssignableFrom(ir.weirdnet.client.ui.addprofile.AddProfileViewModel::class.java) ->
                ir.weirdnet.client.ui.addprofile.AddProfileViewModel(app.profileRepository) as T
            modelClass.isAssignableFrom(ir.weirdnet.client.ui.settings.SettingsViewModel::class.java) ->
                ir.weirdnet.client.ui.settings.SettingsViewModel(app.settingsRepository, app.profileRepository) as T
            modelClass.isAssignableFrom(ir.weirdnet.client.ui.diagnostics.DiagnosticsViewModel::class.java) ->
                ir.weirdnet.client.ui.diagnostics.DiagnosticsViewModel(app.database.logDao(), app.profileRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
