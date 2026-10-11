package cn.mafei.cmp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeArkUIViewController
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.initMainHandler
import kotlin.experimental.ExperimentalNativeApi
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_env
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_value
import platform.ArkTS.ArkTS_Napi_NativeModule.napi_throw_error
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.toKString
import cn.mafei.cmp.session.ApplicationRuntime
import cn.mafei.cmp.session.ProcessApplication
import cn.mafei.cmp.storage.OhosPrivateCredentialStore
import cn.mafei.cmp.storage.CompatibleOhosCredentialStore
import cn.mafei.cmp.storage.ImportingSettingsStorage
import cn.mafei.cmp.storage.createOhosSettings
import cn.mafei.cmp.storage.legacyCmpSettings
import cn.mafei.cmp.platform.http_ohos.createOhosHttpTransport
import cn.mafei.cmp.platform.secure_storage_ohos.OhosAssetCredentialStore
import cn.mafei.cmp.data.migration.LegacySnapshotPayloadReader
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiInsetsDp
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import platform.PerformanceAnalysisKit.HiLog.LOG_APP
import platform.PerformanceAnalysisKit.HiLog.LOG_ERROR
import platform.PerformanceAnalysisKit.HiLog.OH_LOG_PrintMsg

private val ohosWindowFeatures = mutableStateOf(MaFeiWindowFeatures())
private var ohosPlayerScreenVisible = false
private var ohosPlayerFullscreenRequested = false
private var ohosPlayerOrientationRevision = 0L
private var ohosSystemBackInput: OhosSystemBackInput? = null

/**
 * Ability fallback input for the root Navigation 3 dispatcher. Dispatch through
 * the handler chain (including sheets and player menus), never pop a route here.
 */
private class OhosSystemBackInput : NavigationEventInput() {
    private var hasEnabledHandlers = false

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        this.hasEnabledHandlers = hasEnabledHandlers
    }

    fun dispatchBack(): Boolean {
        if (!hasEnabledHandlers) return false
        dispatchOnBackCompleted()
        return true
    }
}

@OptIn(ExperimentalNativeApi::class)
@CName("IsOhosPlayerScreenVisible")
fun IsOhosPlayerScreenVisible(): Boolean = ohosPlayerScreenVisible

@OptIn(ExperimentalNativeApi::class)
@CName("SetOhosApplicationForeground")
fun SetOhosApplicationForeground(foreground: Boolean) = ProcessApplication.setForeground(foreground)

@OptIn(ExperimentalNativeApi::class)
@CName("IsOhosPlayerFullscreenRequested")
fun IsOhosPlayerFullscreenRequested(): Boolean = ohosPlayerFullscreenRequested

/** Increments for every explicit player orientation-button request. */
@OptIn(ExperimentalNativeApi::class)
@CName("GetOhosPlayerOrientationRevision")
fun GetOhosPlayerOrientationRevision(): Long = ohosPlayerOrientationRevision

@OptIn(ExperimentalNativeApi::class)
@CName("HandleOhosComposeBack")
fun HandleOhosComposeBack(): Boolean =
    ohosSystemBackInput?.dispatchBack() == true

@OptIn(ExperimentalNativeApi::class)
@CName("UpdateOhosWindowInsets")
fun UpdateOhosWindowInsets(left: Double, top: Double, right: Double, bottom: Double) {
    fun Double.safeInset(): Float = takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.toFloat() ?: 0f
    ohosWindowFeatures.value = ohosWindowFeatures.value.copy(
        safeInsets = MaFeiInsetsDp(
            left = left.safeInset(),
            top = top.safeInset(),
            right = right.safeInset(),
            bottom = bottom.safeInset(),
        ),
    )
}

/**
 * Publishes the current OHOS host class and width. A tablet freeform window
 * can be wide and can have a native title bar, so it must stay on the tablet
 * shell; only a real HarmonyOS PC/2-in-1 host gets the desktop shell.
 */
@OptIn(ExperimentalNativeApi::class)
@CName("UpdateOhosWindowMetrics")
fun UpdateOhosWindowMetrics(
    widthDp: Double,
    _heightDp: Double,
    desktopHost: Boolean,
    windowedHost: Boolean,
) {
    val width = widthDp.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.toFloat() ?: 0f
    ohosWindowFeatures.value = ohosWindowFeatures.value.copy(
        // The width guard keeps narrow 2-in-1 windows in the tablet layout;
        // the host guard keeps both full-screen and freeform tablets out of PC.
        desktopWindow = desktopHost && width >= 1000f,
        windowedHost = windowedHost,
    )
}

/**
 * Publishes native HarmonyOS window chrome separately from safe areas. The
 * title-button rectangle is not a display cutout; it only reserves the top
 * end of the player chrome while the native buttons are visible.
 */
@OptIn(ExperimentalNativeApi::class)
@CName("UpdateOhosWindowChrome")
fun UpdateOhosWindowChrome(
    windowControlEndInsetDp: Double,
    windowControlTopInsetDp: Double,
    windowFullscreen: Boolean,
) {
    val endInset = windowControlEndInsetDp
        .takeIf { it.isFinite() }
        ?.coerceAtLeast(0.0)
        ?.toFloat()
        ?: 0f
    val topInset = windowControlTopInsetDp
        .takeIf { it.isFinite() }
        ?.coerceAtLeast(0.0)
        ?.toFloat()
        ?: 0f
    ohosWindowFeatures.value = ohosWindowFeatures.value.copy(
        windowControlEndInsetDp = endInset,
        windowControlTopInsetDp = topInset,
        windowFullscreen = windowFullscreen,
    )
}

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class)
@CName("MainArkUIViewController")
fun MainArkUIViewController(
    env: napi_env,
    filesDirectory: CPointer<ByteVar>?,
    bundleName: CPointer<ByteVar>?,
    legacySnapshot: CPointer<ByteVar>?,
    legacyCmpSettingsPayload: CPointer<ByteVar>?,
): napi_value? = try {
    initMainHandler(env)
    val directory = requireNotNull(filesDirectory).toKString()
    cn.mafei.cmp.common.media.configureOhosArtworkCache(directory)
    val applicationBundle = requireNotNull(bundleName).toKString()
    val legacyPayload = requireNotNull(legacySnapshot).toKString()
    val legacyCmpPayload = requireNotNull(legacyCmpSettingsPayload).toKString()
    val runtime = ProcessApplication.getOrCreate {
        val settings = ImportingSettingsStorage(
            createOhosSettings(directory),
            legacyCmpSettings(legacyCmpPayload),
        )
        ApplicationRuntime(
            settings,
            CompatibleOhosCredentialStore(
                primary = OhosAssetCredentialStore(directory, "cn.mafei.credentials.v3"),
                legacy = listOf(
                    // Development builds temporarily used an owner-only private file while
                    // AssetStore was not linked. Copy those credentials forward on first read.
                    OhosPrivateCredentialStore(directory),
                    OhosAssetCredentialStore(directory, "cn.mafei.credentials.v1"),
                    OhosAssetCredentialStore(directory, "cn.mafei.credentials.v2"),
                ),
                settings = settings,
            ),
            createOhosHttpTransport(),
            playerRuntimeFactory = { createOhosPlayerRuntime() },
            // 播放页左侧调亮度、右侧调音量：亮度/音量只能经 ArkUI 拿到，所以值由
            // MaFeiPlayerSurface 的 ArkTS 宿主推送，写入也回给它执行。
            deviceControls = OhosDeviceControls,
            playerWindowControls = OhosPlayerWindowControls,
            photoSaver = OhosPhotoSaver,
            legacySnapshotReader = LegacySnapshotPayloadReader.create(legacyPayload),
            failureReporter = { stage, failure ->
                val diagnostic = if (failure is IllegalStateException) {
                    when (failure.message) {
                        "Nested credential transactions are unsupported" -> "nested-credential-transaction"
                        "Credential transaction required" -> "credential-transaction-required"
                        "Migration is already running" -> "migration-already-running"
                        "Application is closed" -> "application-closed"
                        "Application sessions are closed" -> "sessions-closed"
                        else -> "unknown-illegal-state"
                    }
                } else null
                OH_LOG_PrintMsg(
                    LOG_APP,
                    LOG_ERROR,
                    0x4D46u,
                    "MaFeiSession",
                    buildString {
                        append(stage)
                        append(':')
                        append(failure::class.simpleName ?: "Exception")
                        if (diagnostic != null) {
                            append(':')
                            append(diagnostic)
                        }
                    },
                )
                if (failure is IllegalStateException && diagnostic == "unknown-illegal-state") {
                    OH_LOG_PrintMsg(
                        LOG_APP,
                        LOG_ERROR,
                        0x4D46u,
                        "MaFeiSession",
                        "migration-trace:" + failure.stackTraceToString().replace('\n', '|').take(768),
                    )
                }
            },
            persistenceStageReporter = { stage ->
                OH_LOG_PrintMsg(
                    LOG_APP,
                    LOG_ERROR,
                    0x4D46u,
                    "MaFeiSession",
                    "session-persistence:$stage",
                )
            },
        )
    }
    ComposeArkUIViewController(env) {
        // ComposeArkUIViewController has no Android ViewTree owner. Install a
        // platform-backed root so Navigation 3 receives HarmonyOS back events.
        val navigationOwner = rememberNavigationEventDispatcherOwner(parent = null)
        val systemBackInput = remember(navigationOwner) { OhosSystemBackInput() }
        DisposableEffect(navigationOwner, systemBackInput) {
            val dispatcher = navigationOwner.navigationEventDispatcher
            dispatcher.addInput(systemBackInput)
            ohosSystemBackInput = systemBackInput
            onDispose {
                if (ohosSystemBackInput === systemBackInput) {
                    ohosSystemBackInput = null
                }
                dispatcher.removeInput(systemBackInput)
            }
        }
        val windowFeatures by ohosWindowFeatures
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides navigationOwner,
            LocalMaFeiWindowFeatures provides windowFeatures,
            LocalEmbeddedWebHost provides OhosEmbeddedWebHost,
            cn.mafei.cmp.player.ui.LocalPlayerVideoBackdropSource provides ohosVideoBackdropSource,
        ) {
            App(
                runtime = runtime,
                playerSurface = { controller, onBack, onExitFullscreen, fullscreen, modifier ->
                    OhosPlayerSurface(
                        controller = controller,
                        onBack = onBack,
                        onExitFullscreen = onExitFullscreen,
                        fullscreen = fullscreen,
                        modifier = modifier,
                    )
                },
                onPlayerScreenChanged = { visible ->
                    ohosPlayerScreenVisible = visible
                    if (!visible) ohosPlayerFullscreenRequested = false
                },
                onPlayerFullscreenChanged = { fullscreen ->
                    ohosPlayerFullscreenRequested = fullscreen
                    ohosPlayerOrientationRevision++
                },
                playerRenderModeSupported = true,
            )
        }
    }
} catch (_: Exception) {
    // A Kotlin exception must not escape the C callback and terminate the process.
    napi_throw_error(env, null, "Application runtime initialization failed")
    null
}
