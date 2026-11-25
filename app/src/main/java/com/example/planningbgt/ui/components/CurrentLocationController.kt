package com.example.planningbgt.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.planningbgt.location.LocationFetchResult
import com.example.planningbgt.location.LocationProvider
import com.example.planningbgt.location.LocationService
import com.example.planningbgt.model.UserLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Estados que la UI puede mostrar (HU-12).
sealed interface LocationState {
    // Aún no se ha intentado nada
    data object Idle : LocationState

    // Sin permiso y todavía no se ha pedido al sistema (prompt pendiente)
    data object PermissionNeeded : LocationState

    data object Loading : LocationState

    data class Success(val location: UserLocation, val fromCache: Boolean) : LocationState

    // canAskAgain = true: el sistema todavía muestra el diálogo. false: bloqueado, solo se arregla en Ajustes
    data class PermissionDenied(val canAskAgain: Boolean) : LocationState

    data object GpsDisabled : LocationState
    data object Timeout : LocationState
    data object Unavailable : LocationState
}

// Equivalente a un "hook" useCurrentLocation: sostiene el estado y las acciones de ubicación.
// Sin ViewModel (convención del proyecto): vive con remember dentro de la pantalla. La caché
// compartida de LocationService hace que, al rotar o cambiar de pestaña, la ubicación vuelva sin esperar.
@Stable
class CurrentLocationController internal constructor(
    private val provider: LocationProvider,
    private val scope: CoroutineScope,
    private val hasPermission: () -> Boolean,
    private val canAskPermissionAgain: () -> Boolean,
    private val openAppSettingsAction: () -> Unit,
    private val openLocationSettingsAction: () -> Unit
) {
    var state: LocationState by mutableStateOf<LocationState>(LocationState.Idle)
        private set

    // Última ubicación obtenida en esta sesión (para dibujar el pin). Se borra si se pierde el permiso.
    var location: UserLocation? by mutableStateOf<UserLocation?>(null)
        private set

    // true => mostrar el diálogo que explica por qué se pide la ubicación (antes del diálogo del sistema)
    var showRationale: Boolean by mutableStateOf(false)
        private set

    // Sube cada vez que llega una ubicación nueva (sirve de disparador para efectos de la UI)
    var fixCount: Int by mutableIntStateOf(0)
        private set

    // El usuario pulsó "Mi ubicación": al llegar la ubicación hay que centrar el mapa
    var pendingCenter: Boolean by mutableStateOf(false)
        private set

    // Lo asigna rememberCurrentLocation (necesita el launcher de permisos, que es de Compose)
    internal var launchPermissionRequest: () -> Unit = {}

    private var job: Job? = null

    // Se llama una vez al entrar a la pantalla
    fun start() {
        if (state != LocationState.Idle) return
        if (hasPermission()) load(forceRefresh = false) else askForPermission()
    }

    // "Actualizar ubicación" / "Reintentar". Según el estado, pide permiso, abre ajustes o consulta de nuevo.
    fun refresh(centerOnFix: Boolean = false) {
        pendingCenter = centerOnFix
        if (hasPermission()) {
            load(forceRefresh = true)
            return
        }
        val current = state
        if (current is LocationState.PermissionDenied) {
            if (current.canAskAgain) launchPermissionRequest() else openAppSettingsAction()
        } else {
            askForPermission()
        }
    }

    fun onRationaleConfirmed() {
        showRationale = false
        launchPermissionRequest()
    }

    fun onRationaleDismissed() {
        showRationale = false
        pendingCenter = false
    }

    internal fun onPermissionResult(granted: Boolean) {
        if (granted) {
            load(forceRefresh = true)
        } else {
            pendingCenter = false
            state = LocationState.PermissionDenied(canAskAgain = canAskPermissionAgain())
        }
    }

    fun openAppSettings() = openAppSettingsAction()

    fun openLocationSettings() = openLocationSettingsAction()

    fun consumeCenter() {
        pendingCenter = false
    }

    // Al volver de Ajustes: si el usuario dio el permiso o encendió la ubicación, se reintenta solo
    internal fun onResume() {
        val current = state
        when {
            current is LocationState.PermissionDenied && hasPermission() -> load(forceRefresh = true)
            current is LocationState.GpsDisabled -> load(forceRefresh = true)
        }
    }

    private fun askForPermission() {
        state = LocationState.PermissionNeeded
        showRationale = true
    }

    private fun load(forceRefresh: Boolean) {
        job?.cancel()
        state = LocationState.Loading
        job = scope.launch {
            when (val result = provider.getCurrentLocation(forceRefresh)) {
                is LocationFetchResult.Success -> {
                    location = result.location
                    state = LocationState.Success(result.location, result.fromCache)
                    fixCount++
                }

                LocationFetchResult.PermissionDenied -> {
                    location = null // sin permiso no se conserva ninguna ubicación
                    pendingCenter = false
                    state = LocationState.PermissionNeeded
                }

                LocationFetchResult.GpsDisabled -> fail(LocationState.GpsDisabled)
                LocationFetchResult.Timeout -> fail(LocationState.Timeout)
                LocationFetchResult.Unavailable -> fail(LocationState.Unavailable)
            }
        }
    }

    private fun fail(newState: LocationState) {
        pendingCenter = false
        state = newState
    }
}

@Composable
fun rememberCurrentLocation(): CurrentLocationController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val provider = remember { LocationService.create(context) }

    val controller = remember {
        CurrentLocationController(
            provider = provider,
            scope = scope,
            hasPermission = { context.hasLocationPermission() },
            canAskPermissionAgain = { context.canAskLocationPermissionAgain() },
            openAppSettingsAction = { context.openAppSettings() },
            openLocationSettingsAction = { context.openLocationSettings() }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Basta con uno: "ubicación aproximada" también sirve para eventos cercanos
        controller.onPermissionResult(grants.values.any { it })
    }

    SideEffect {
        controller.launchPermissionRequest = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(controller) { controller.start() }

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return controller
}

// ── Helpers de Android ──

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

// true si el sistema todavía mostrará el diálogo (el usuario lo negó una vez, no "para siempre")
private fun Context.canAskLocationPermissionAgain(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.ACCESS_FINE_LOCATION
    ) || ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.ACCESS_COARSE_LOCATION
    )
}

private fun Context.openAppSettings() {
    startActivitySafely(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    )
}

private fun Context.openLocationSettings() {
    startActivitySafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

private fun Context.startActivitySafely(intent: Intent) {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Dispositivo sin esa pantalla de ajustes: no hay nada más que hacer
    }
}