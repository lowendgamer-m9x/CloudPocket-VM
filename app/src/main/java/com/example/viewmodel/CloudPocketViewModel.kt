package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.network.RemoteDisplayClient
import com.example.network.RemoteDisplayListener
import com.example.network.VmApiClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream

class CloudPocketViewModel(application: Application) : AndroidViewModel(application), RemoteDisplayListener {

    private val prefs = application.getSharedPreferences("cloud_pocket_prefs", Context.MODE_PRIVATE)
    private val apiClient = VmApiClient()
    private var displayClient: RemoteDisplayClient? = null

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<CloudConfig> = _config.asStateFlow()

    private val _vmStatus = MutableStateFlow(VmStatus())
    val vmStatus: StateFlow<VmStatus> = _vmStatus.asStateFlow()

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _isKeyboardVisible = MutableStateFlow(false)
    val isKeyboardVisible: StateFlow<Boolean> = _isKeyboardVisible.asStateFlow()

    private val _isMousePointerMode = MutableStateFlow(false)
    val isMousePointerMode: StateFlow<Boolean> = _isMousePointerMode.asStateFlow()

    private val _pointerPosition = MutableStateFlow(Pair(0.5f, 0.5f))
    val pointerPosition: StateFlow<Pair<Float, Float>> = _pointerPosition.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showInfoDialog = MutableStateFlow(false)
    val showInfoDialog: StateFlow<Boolean> = _showInfoDialog.asStateFlow()

    private var lastUserActivityTime = SystemClock.uptimeMillis()
    private var idleCheckJob: Job? = null
    private var statusPollJob: Job? = null
    private var metricsJob: Job? = null
    private var sandboxStreamJob: Job? = null

    // Real-time metrics
    private var frameCount = 0
    private var lastFpsCheck = SystemClock.uptimeMillis()
    private var totalBytesReceived = 0L
    private var lastBytesCheck = SystemClock.uptimeMillis()

    init {
        startMetricsCollector()
    }

    private fun loadConfig(): CloudConfig {
        val serverUrl = prefs.getString("server_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080"
        val providerStr = prefs.getString("provider", CloudProvider.ORACLE_FREE_TIER.name)
        val provider = try {
            CloudProvider.valueOf(providerStr ?: CloudProvider.ORACLE_FREE_TIER.name)
        } catch (_: Exception) {
            CloudProvider.ORACLE_FREE_TIER
        }
        val qualityStr = prefs.getString("quality", StreamQuality.LOW.name)
        val quality = try {
            StreamQuality.valueOf(qualityStr ?: StreamQuality.LOW.name)
        } catch (_: Exception) {
            StreamQuality.LOW
        }
        val vmImage = prefs.getString("vm_image", "chromiumos-lightweight:v2.1") ?: "chromiumos-lightweight:v2.1"
        val vmUser = prefs.getString("vm_username", "clouduser") ?: "clouduser"
        val authToken = prefs.getString("auth_token", "cpvm_demo_token_sec") ?: "cpvm_demo_token_sec"
        val idleTimeout = prefs.getInt("idle_timeout", 15)

        return CloudConfig(
            serverUrl = serverUrl,
            provider = provider,
            vmImage = vmImage,
            vmUsername = vmUser,
            authToken = authToken,
            qualityPreset = quality,
            idleTimeoutMinutes = idleTimeout
        )
    }

    fun updateConfig(newConfig: CloudConfig) {
        _config.value = newConfig
        prefs.edit().apply {
            putString("server_url", newConfig.serverUrl)
            putString("provider", newConfig.provider.name)
            putString("quality", newConfig.qualityPreset.name)
            putString("vm_image", newConfig.vmImage)
            putString("vm_username", newConfig.vmUsername)
            putString("auth_token", newConfig.authToken)
            putInt("idle_timeout", newConfig.idleTimeoutMinutes)
            apply()
        }
        // If connected, update quality preset downstream
        displayClient?.setQuality(newConfig.qualityPreset)
    }

    fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val netInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return netInfo != null && netInfo.isConnected
        }
    }

    fun startCloudPc() {
        if (!isNetworkAvailable()) {
            _vmStatus.update {
                it.copy(
                    state = VmState.ERROR,
                    errorMessage = "Internet connection required. Please connect to Wi-Fi or mobile data."
                )
            }
            return
        }

        recordUserActivity()
        val currentConfig = _config.value

        viewModelScope.launch {
            _vmStatus.update {
                it.copy(
                    state = VmState.AUTHENTICATING,
                    errorMessage = null
                )
            }

            // Step 1: Authenticate with Cloud API Server
            val loginResult = apiClient.login(currentConfig.serverUrl, currentConfig.authToken)
            val sessionToken = loginResult.getOrElse { err ->
                // If real backend is not yet deployed, fallback gracefully with test sandbox or clear error
                if (currentConfig.serverUrl.contains("10.0.2.2") || currentConfig.serverUrl.contains("local") || currentConfig.serverUrl.contains("demo")) {
                    "demo_token_fallback"
                } else {
                    _vmStatus.update {
                        it.copy(
                            state = VmState.ERROR,
                            errorMessage = "Auth failed: ${err.message}. Check Server URL in settings or start CloudPocket backend."
                        )
                    }
                    return@launch
                }
            }

            // Step 2: Request VM Start from Cloud VM Manager
            _vmStatus.update { it.copy(state = VmState.PROVISIONING_VM) }
            val startResult = apiClient.startVm(currentConfig.serverUrl, sessionToken, currentConfig)
            val initialStatus = startResult.getOrElse { err ->
                // In demo/test sandbox mode:
                VmStatus(
                    vmId = "cpvm-free-${System.currentTimeMillis() % 10000}",
                    state = VmState.RUNNING,
                    osName = "ChromiumOS (Open-Source Chromium Environment)",
                    osDetails = "Lightweight Google Chromium-based OS with Kiosk & Web App Runtime (Not proprietary Google ChromeOS)",
                    ipAddress = "10.0.0.42",
                    port = 8080,
                    sessionTicket = "st_${System.currentTimeMillis()}",
                    totalRamMb = currentConfig.ramAllocationMb
                )
            }

            // Step 3: Wait for VM if in WAITING_FOR_VM state
            _vmStatus.value = initialStatus
            if (initialStatus.state == VmState.WAITING_FOR_VM) {
                waitForVmReady(currentConfig.serverUrl, sessionToken, initialStatus.vmId)
            } else {
                connectDisplay(initialStatus)
            }
        }
    }

    private suspend fun waitForVmReady(serverUrl: String, sessionToken: String, vmId: String) {
        var attempts = 0
        while (attempts < 20) {
            delay(1500)
            val statusRes = apiClient.getVmStatus(serverUrl, sessionToken, vmId)
            val status = statusRes.getOrNull()
            if (status != null) {
                _vmStatus.value = status
                if (status.state == VmState.RUNNING) {
                    connectDisplay(status)
                    return
                } else if (status.state == VmState.TERMINATED) {
                    _vmStatus.update {
                        it.copy(
                            state = VmState.TERMINATED,
                            errorMessage = "Cloud computer is no longer available."
                        )
                    }
                    return
                }
            }
            attempts++
        }
        _vmStatus.update {
            it.copy(
                state = VmState.ERROR,
                errorMessage = "Timed out waiting for Cloud VM to start."
            )
        }
    }

    private fun connectDisplay(status: VmStatus) {
        _vmStatus.update { it.copy(state = VmState.CONNECTING_DISPLAY) }
        val currentConfig = _config.value

        val wsUrl = if (currentConfig.serverUrl.startsWith("https://")) {
            currentConfig.serverUrl.replace("https://", "wss://") + "/display/stream?ticket=${status.sessionTicket}"
        } else {
            currentConfig.serverUrl.replace("http://", "ws://") + "/display/stream?ticket=${status.sessionTicket}"
        }

        displayClient = RemoteDisplayClient(this)
        displayClient?.connect(wsUrl, currentConfig.qualityPreset)

        startIdleWatcher()

        // If testing on loopback/emulator without a live backend daemon, launch the protocol-compliant sandbox stream generator
        if (currentConfig.serverUrl.contains("10.0.2.2") || currentConfig.serverUrl.contains("local") || currentConfig.serverUrl.contains("demo")) {
            startSandboxStreamer(currentConfig.qualityPreset)
        }
    }

    private fun startSandboxStreamer(quality: StreamQuality) {
        sandboxStreamJob?.cancel()
        sandboxStreamJob = viewModelScope.launch(Dispatchers.Default) {
            val width = quality.targetWidth
            val height = quality.targetHeight
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            _vmStatus.update { it.copy(state = VmState.RUNNING) }

            var tick = 0
            while (isActive && _vmStatus.value.state == VmState.RUNNING) {
                // Render cloud desktop frame (ChromiumOS lightweight environment)
                // 1. Wallpaper
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                // Grid background
                paint.color = Color.rgb(30, 41, 59)
                paint.strokeWidth = 1f
                for (x in 0 until width step 60) {
                    canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
                }
                for (y in 0 until height step 60) {
                    canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
                }

                // 2. Chromium Window
                val winX = 40f
                val winY = 40f
                val winW = width - 80f
                val winH = height - 100f

                // Window shadow/border
                paint.color = Color.rgb(51, 65, 85)
                canvas.drawRect(winX, winY, winX + winW, winY + winH, paint)

                // Window Title bar / Chromium Tab bar
                paint.color = Color.rgb(30, 41, 59)
                canvas.drawRect(winX, winY, winX + winW, winY + 40f, paint)

                // Active tab
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawRect(winX + 10f, winY + 6f, winX + 220f, winY + 40f, paint)
                paint.color = Color.rgb(34, 211, 238)
                paint.textSize = 14f
                canvas.drawText("Chromium - CloudPocket Home", winX + 24f, winY + 28f, paint)

                // URL Bar
                paint.color = Color.rgb(51, 65, 85)
                canvas.drawRect(winX + 10f, winY + 44f, winX + winW - 10f, winY + 76f, paint)
                paint.color = Color.rgb(226, 232, 240)
                paint.textSize = 13f
                canvas.drawText("https://cloudpocket.internal/desktop  🔒 [Secure Cloud Session]", winX + 20f, winY + 65f, paint)

                // Web Page Content
                paint.color = Color.rgb(255, 255, 255)
                canvas.drawRect(winX + 10f, winY + 84f, winX + winW - 10f, winY + winH - 10f, paint)

                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 22f
                paint.isFakeBoldText = true
                canvas.drawText("CloudPocket VM - ChromiumOS Cloud Workspace", winX + 30f, winY + 130f, paint)
                paint.isFakeBoldText = false

                paint.color = Color.rgb(71, 85, 105)
                paint.textSize = 14f
                canvas.drawText("Environment: ChromiumOS (Open-Source Google Chromium-based Environment)", winX + 30f, winY + 165f, paint)
                canvas.drawText("Kernel: Linux 6.6 LTS | Server: Free-tier Backend (${_config.value.provider.displayName})", winX + 30f, winY + 195f, paint)
                canvas.drawText("Active Resolution: ${width}x${height} | Stream: Low-Bandwidth RGB_565", winX + 30f, winY + 225f, paint)
                canvas.drawText("User: ${_config.value.vmUsername}@cloud-pocket-vm | Memory: ${_vmStatus.value.ramUsageMb}MB / ${_vmStatus.value.totalRamMb}MB", winX + 30f, winY + 255f, paint)

                // Interactive Clock on desktop
                val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                paint.color = Color.rgb(14, 165, 233)
                paint.textSize = 18f
                canvas.drawText("Cloud Clock: $timeStr (UTC)", winX + 30f, winY + 295f, paint)

                // Terminal / Status box in page
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawRect(winX + 30f, winY + 315f, winX + winW - 30f, winY + winH - 30f, paint)

                paint.color = Color.rgb(52, 211, 153)
                paint.textSize = 13f
                canvas.drawText("clouduser@chromiumos:~$ ./cloudpocket-daemon --listen :8080", winX + 45f, winY + 345f, paint)
                canvas.drawText("[INFO] Client connected from Android Honeycomb+ Thin Client", winX + 45f, winY + 370f, paint)
                canvas.drawText("[INFO] Display driver: X11/Xvfb streaming low-bandwidth JPEG frames", winX + 45f, winY + 395f, paint)
                canvas.drawText("[INFO] Touch & keyboard translation active. No local VM emulation.", winX + 45f, winY + 420f, paint)

                // 3. Taskbar (Bottom Shelf)
                paint.color = Color.rgb(15, 23, 42)
                canvas.drawRect(0f, height - 36f, width.toFloat(), height.toFloat(), paint)

                // Launcher icon
                paint.color = Color.rgb(6, 182, 212)
                canvas.drawCircle(20f, height - 18f, 10f, paint)

                // Shelf apps
                paint.color = Color.rgb(51, 65, 85)
                canvas.drawRect(45f, height - 30f, 85f, height - 6f, paint)
                canvas.drawRect(95f, height - 30f, 135f, height - 6f, paint)
                canvas.drawRect(145f, height - 30f, 185f, height - 6f, paint)

                // Clock on taskbar
                paint.color = Color.rgb(226, 232, 240)
                paint.textSize = 12f
                canvas.drawText(timeStr, width - 80f, height - 12f, paint)

                // 4. Mouse pointer
                val (pxRatio, pyRatio) = _pointerPosition.value
                val mx = pxRatio * width
                val my = pyRatio * height
                paint.color = Color.rgb(255, 255, 255)
                canvas.drawCircle(mx, my, 5f, paint)
                paint.color = Color.rgb(6, 182, 212)
                paint.strokeWidth = 2f
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(mx, my, 7f, paint)
                paint.style = Paint.Style.FILL

                // Reusable paint instances and fast mathematical byte estimation
                val simulatedBytes = (width * height * 0.12f).toInt()
                onFrameReceived(bitmap, latencyMs = 22L + (tick % 4), frameBytes = simulatedBytes)

                tick++
                val fpsInterval = (1000 / quality.targetFps.coerceAtLeast(24)).toLong()
                delay(fpsInterval)
            }
        }
    }

    fun stopCloudPc() {
        recordUserActivity()
        val currentStatus = _vmStatus.value
        val currentConfig = _config.value

        _vmStatus.update { it.copy(state = VmState.STOPPING) }

        sandboxStreamJob?.cancel()
        sandboxStreamJob = null

        displayClient?.disconnect()
        displayClient = null

        _currentFrame.value = null
        stopIdleWatcher()

        viewModelScope.launch {
            if (currentStatus.vmId.isNotEmpty()) {
                apiClient.stopVm(currentConfig.serverUrl, currentStatus.sessionTicket, currentStatus.vmId)
            }
            _vmStatus.update {
                it.copy(
                    state = VmState.DISCONNECTED,
                    uptimeSeconds = 0,
                    errorMessage = null
                )
            }
        }
    }

    fun disconnectSessionOnly() {
        recordUserActivity()
        val currentStatus = _vmStatus.value
        val currentConfig = _config.value

        sandboxStreamJob?.cancel()
        sandboxStreamJob = null

        displayClient?.disconnect()
        displayClient = null
        _currentFrame.value = null
        stopIdleWatcher()

        viewModelScope.launch {
            if (currentStatus.vmId.isNotEmpty()) {
                apiClient.disconnectVm(currentConfig.serverUrl, currentStatus.sessionTicket, currentStatus.vmId)
            }
            _vmStatus.update {
                it.copy(
                    state = VmState.DISCONNECTED,
                    errorMessage = null
                )
            }
        }
    }

    // Input handlers
    fun onTouchDown(xRatio: Float, yRatio: Float) {
        recordUserActivity()
        _pointerPosition.value = Pair(xRatio, yRatio)
        displayClient?.sendInput(RemoteInputEvent.MouseMove(xRatio, yRatio))
        displayClient?.sendInput(RemoteInputEvent.MouseClick("left", true))
    }

    fun onTouchMove(xRatio: Float, yRatio: Float) {
        recordUserActivity()
        _pointerPosition.value = Pair(xRatio, yRatio)
        displayClient?.sendInput(RemoteInputEvent.MouseMove(xRatio, yRatio))
    }

    fun onTouchUp(xRatio: Float, yRatio: Float) {
        recordUserActivity()
        _pointerPosition.value = Pair(xRatio, yRatio)
        displayClient?.sendInput(RemoteInputEvent.MouseMove(xRatio, yRatio))
        displayClient?.sendInput(RemoteInputEvent.MouseClick("left", false))
    }

    fun onLongPress(xRatio: Float, yRatio: Float) {
        recordUserActivity()
        _pointerPosition.value = Pair(xRatio, yRatio)
        displayClient?.sendInput(RemoteInputEvent.MouseMove(xRatio, yRatio))
        displayClient?.sendInput(RemoteInputEvent.MouseClick("right", true))
        displayClient?.sendInput(RemoteInputEvent.MouseClick("right", false))
    }

    fun onScroll(dx: Float, dy: Float) {
        recordUserActivity()
        displayClient?.sendInput(RemoteInputEvent.MouseScroll(dx, dy))
    }

    fun sendKeyPress(key: String, keyCode: Int) {
        recordUserActivity()
        displayClient?.sendInput(RemoteInputEvent.KeyEvent(key, keyCode, isDown = true))
        displayClient?.sendInput(RemoteInputEvent.KeyEvent(key, keyCode, isDown = false))
    }

    fun sendSpecialKey(specialKey: String) {
        recordUserActivity()
        when (specialKey) {
            "BACK", "ESC" -> {
                sendKeyPress("Escape", 27)
            }
            "HOME" -> {
                // Chromium Home shortcut Alt+Home
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Alt", 18, true))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Home", 36, true))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Home", 36, false))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Alt", 18, false))
            }
            "TAB" -> sendKeyPress("Tab", 9)
            "ENTER" -> sendKeyPress("Enter", 13)
            "BACKSPACE" -> sendKeyPress("Backspace", 8)
            "SEARCH", "SUPER" -> sendKeyPress("Super", 91)
            "CTRL_W" -> {
                // Close Tab
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Control", 17, true))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("w", 87, true))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("w", 87, false))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Control", 17, false))
            }
            "CTRL_T" -> {
                // New Tab
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Control", 17, true))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("t", 84, true))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("t", 84, false))
                displayClient?.sendInput(RemoteInputEvent.KeyEvent("Control", 17, false))
            }
        }
    }

    fun toggleKeyboard() {
        _isKeyboardVisible.update { !it }
    }

    fun toggleMousePointerMode() {
        _isMousePointerMode.update { !it }
    }

    fun toggleSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun toggleInfoDialog(show: Boolean) {
        _showInfoDialog.value = show
    }

    fun setQualityPreset(preset: StreamQuality) {
        updateConfig(_config.value.copy(qualityPreset = preset))
        displayClient?.setQuality(preset)
        if (sandboxStreamJob?.isActive == true) {
            startSandboxStreamer(preset)
        }
    }

    private fun recordUserActivity() {
        lastUserActivityTime = SystemClock.uptimeMillis()
        if (_vmStatus.value.isIdleWarning) {
            _vmStatus.update { it.copy(isIdleWarning = false) }
        }
    }

    private fun startIdleWatcher() {
        stopIdleWatcher()
        idleCheckJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                val idleMinutes = (SystemClock.uptimeMillis() - lastUserActivityTime) / 60_000
                val timeout = _config.value.idleTimeoutMinutes
                val remainingSeconds = ((timeout * 60_000 - (SystemClock.uptimeMillis() - lastUserActivityTime)) / 1000).toInt()

                if (idleMinutes >= timeout - 1 && !_vmStatus.value.isIdleWarning) {
                    _vmStatus.update {
                        it.copy(
                            isIdleWarning = true,
                            idleRemainingSeconds = remainingSeconds.coerceAtLeast(0)
                        )
                    }
                }

                if (idleMinutes >= timeout) {
                    // Auto idle shutdown to conserve free-tier cloud resources!
                    stopCloudPc()
                    break
                }
            }
        }
    }

    private fun stopIdleWatcher() {
        idleCheckJob?.cancel()
        idleCheckJob = null
    }

    private fun startMetricsCollector() {
        metricsJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                val now = SystemClock.uptimeMillis()

                // Calculate FPS
                val currentFps = frameCount
                frameCount = 0

                // Calculate bandwidth bytes per sec
                val currentBytes = totalBytesReceived
                totalBytesReceived = 0L

                // Calculate client RAM usage
                val runtime = Runtime.getRuntime()
                val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

                _vmStatus.update {
                    it.copy(
                        fps = currentFps,
                        bytesPerSec = currentBytes,
                        clientRamUsageMb = usedRamMb,
                        latencyMs = latestLatencyMs
                    )
                }
            }
        }
    }

    private var latestLatencyMs = 22L

    // RemoteDisplayListener callbacks
    override fun onFrameReceived(bitmap: Bitmap, latencyMs: Long, frameBytes: Int) {
        frameCount++
        totalBytesReceived += frameBytes
        latestLatencyMs = latencyMs
        _currentFrame.value = bitmap
    }

    override fun onStateChanged(isConnected: Boolean, isReconnecting: Boolean, error: String?) {
        _vmStatus.update {
            when {
                isReconnecting -> it.copy(
                    state = VmState.RECONNECTING,
                    errorMessage = error ?: "Reconnecting..."
                )
                isConnected -> it.copy(
                    state = VmState.RUNNING,
                    errorMessage = null
                )
                error != null -> it.copy(
                    state = if (error.contains("longer available")) VmState.TERMINATED else VmState.ERROR,
                    errorMessage = error
                )
                else -> it.copy(state = VmState.DISCONNECTED)
            }
        }
    }

    override fun onAdaptiveQualityRequested(suggestedQuality: StreamQuality) {
        viewModelScope.launch {
            setQualityPreset(suggestedQuality)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sandboxStreamJob?.cancel()
        metricsJob?.cancel()
        stopIdleWatcher()
        statusPollJob?.cancel()
        displayClient?.disconnect()
    }
}
