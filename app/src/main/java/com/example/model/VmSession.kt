package com.example.model

enum class VmState {
    DISCONNECTED,
    AUTHENTICATING,
    PROVISIONING_VM,
    WAITING_FOR_VM,
    CONNECTING_DISPLAY,
    RUNNING,
    RECONNECTING,
    STOPPING,
    TERMINATED,
    ERROR
}

data class VmStatus(
    val vmId: String = "",
    val state: VmState = VmState.DISCONNECTED,
    val osName: String = "ChromiumOS (Open-Source Chromium Environment)",
    val osDetails: String = "Lightweight Google Chromium-based OS with Kiosk & Web App Runtime (Not proprietary Google ChromeOS)",
    val ipAddress: String = "10.0.0.42",
    val port: Int = 8080,
    val sessionTicket: String = "",
    val uptimeSeconds: Long = 0,
    val cpuUsagePercent: Int = 12,
    val ramUsageMb: Int = 340,
    val totalRamMb: Int = 1024,
    val latencyMs: Long = 0,
    val fps: Int = 0,
    val bytesPerSec: Long = 0,
    val clientRamUsageMb: Long = 0,
    val errorMessage: String? = null,
    val isIdleWarning: Boolean = false,
    val idleRemainingSeconds: Int = 900
)
