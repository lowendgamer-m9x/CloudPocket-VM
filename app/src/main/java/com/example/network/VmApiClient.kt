package com.example.network

import com.example.model.CloudConfig
import com.example.model.VmState
import com.example.model.VmStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class VmApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(serverUrl: String, authToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("token", authToken)
                put("client", "CloudPocket-Android-ThinClient")
                put("version", "1.0-ultra-light")
            }
            val request = Request.Builder()
                .url("${serverUrl.trimEnd('/')}/login")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Authentication failed: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val sessionToken = json.optString("sessionToken", authToken)
                Result.success(sessionToken)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startVm(serverUrl: String, sessionToken: String, config: CloudConfig): Result<VmStatus> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("provider", config.provider.name)
                    put("image", config.vmImage)
                    put("username", config.vmUsername)
                    put("ramMb", config.ramAllocationMb)
                    put("cpuCores", config.cpuCores)
                    put("storageGb", config.storageAllocationGb)
                    put("idleTimeoutMinutes", config.idleTimeoutMinutes)
                    put("maxLifetimeMinutes", config.maxLifetimeMinutes)
                    put("qualityPreset", config.qualityPreset.name)
                }

                val request = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/vm/start")
                    .header("Authorization", "Bearer $sessionToken")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IOException("Failed to start cloud VM: HTTP ${response.code}"))
                    }
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    Result.success(parseVmStatus(json))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getVmStatus(serverUrl: String, sessionToken: String, vmId: String): Result<VmStatus> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/vm/status?vmId=$vmId")
                    .header("Authorization", "Bearer $sessionToken")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 404) {
                            return@withContext Result.success(
                                VmStatus(
                                    vmId = vmId,
                                    state = VmState.TERMINATED,
                                    errorMessage = "Cloud computer is no longer available."
                                )
                            )
                        }
                        return@withContext Result.failure(IOException("Failed to query status: HTTP ${response.code}"))
                    }
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    Result.success(parseVmStatus(json))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun stopVm(serverUrl: String, sessionToken: String, vmId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("vmId", vmId)
                    put("action", "shutdown")
                    put("releaseResources", true)
                }
                val request = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/vm/stop")
                    .header("Authorization", "Bearer $sessionToken")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    Result.success(response.isSuccessful)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun disconnectVm(serverUrl: String, sessionToken: String, vmId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("vmId", vmId)
                }
                val request = Request.Builder()
                    .url("${serverUrl.trimEnd('/')}/vm/disconnect")
                    .header("Authorization", "Bearer $sessionToken")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    Result.success(response.isSuccessful)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseVmStatus(json: JSONObject): VmStatus {
        val stateStr = json.optString("state", "RUNNING").uppercase()
        val state = try {
            VmState.valueOf(stateStr)
        } catch (_: Exception) {
            if (stateStr == "READY" || stateStr == "ACTIVE") VmState.RUNNING else VmState.WAITING_FOR_VM
        }

        return VmStatus(
            vmId = json.optString("vmId", "vm-${System.currentTimeMillis() % 10000}"),
            state = state,
            osName = json.optString("osName", "ChromiumOS (Open-Source Chromium Environment)"),
            osDetails = json.optString(
                "osDetails",
                "Lightweight Google Chromium-based OS with Kiosk & Web App Runtime (Not proprietary Google ChromeOS)"
            ),
            ipAddress = json.optString("ipAddress", "remote-vm.cloudpocket.net"),
            port = json.optInt("port", 8080),
            sessionTicket = json.optString("sessionTicket", ""),
            uptimeSeconds = json.optLong("uptimeSeconds", 1),
            cpuUsagePercent = json.optInt("cpuUsagePercent", 14),
            ramUsageMb = json.optInt("ramUsageMb", 340),
            totalRamMb = json.optInt("totalRamMb", 1024),
            idleRemainingSeconds = json.optInt("idleRemainingSeconds", 900)
        )
    }
}
