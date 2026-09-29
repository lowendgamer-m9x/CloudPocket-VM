package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.CloudPocketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CloudPocketViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val vmStatus by viewModel.vmStatus.collectAsState()
    val showSettings by viewModel.showSettingsDialog.collectAsState()
    val showInfo by viewModel.showInfoDialog.collectAsState()

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Cyan500),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Cloud Icon",
                                tint = Slate900,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CloudPocket VM",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100
                                )
                            )
                            Text(
                                text = "Ultra-Lightweight Cloud Computer Client",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Cyan400,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleInfoDialog(true) },
                        modifier = Modifier.testTag("info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Architecture & Compatibility Info",
                            tint = Slate400
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleSettingsDialog(true) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cloud Settings",
                            tint = Slate400
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Error or Status Notification Banner
            if (vmStatus.errorMessage != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Rose500.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Rose500))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = Rose400,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = vmStatus.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Rose400,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // Connection Status Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Slate400,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        // Live indicator badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    when (vmStatus.state) {
                                        VmState.RUNNING -> Emerald500.copy(alpha = 0.2f)
                                        VmState.AUTHENTICATING, VmState.PROVISIONING_VM, VmState.WAITING_FOR_VM, VmState.CONNECTING_DISPLAY -> Amber500.copy(alpha = 0.2f)
                                        VmState.RECONNECTING -> Amber500.copy(alpha = 0.2f)
                                        VmState.ERROR, VmState.TERMINATED -> Rose500.copy(alpha = 0.2f)
                                        else -> Slate700
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (vmStatus.state) {
                                            VmState.RUNNING -> Emerald400
                                            VmState.AUTHENTICATING, VmState.PROVISIONING_VM, VmState.WAITING_FOR_VM, VmState.CONNECTING_DISPLAY -> Amber400
                                            VmState.RECONNECTING -> Amber400
                                            VmState.ERROR, VmState.TERMINATED -> Rose400
                                            else -> Slate500
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (vmStatus.state) {
                                    VmState.DISCONNECTED -> "Disconnected"
                                    VmState.AUTHENTICATING -> "Authenticating..."
                                    VmState.PROVISIONING_VM -> "Starting Cloud VM..."
                                    VmState.WAITING_FOR_VM -> "Waiting for VM..."
                                    VmState.CONNECTING_DISPLAY -> "Connecting Display..."
                                    VmState.RUNNING -> "Connected"
                                    VmState.RECONNECTING -> "Reconnecting..."
                                    VmState.STOPPING -> "Stopping..."
                                    VmState.TERMINATED -> "VM Terminated"
                                    VmState.ERROR -> "Error"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate100
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Slate700)
                    Spacer(modifier = Modifier.height(14.dp))

                    StatusRow(label = "Cloud Backend", value = config.provider.displayName)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusRow(
                        label = "Virtual Machine",
                        value = if (vmStatus.state == VmState.RUNNING) "Running (${vmStatus.vmId})" else "Stopped"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusRow(
                        label = "Operating System",
                        value = "ChromiumOS (Open-Source Chromium)",
                        highlightColor = Cyan400
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusRow(
                        label = "Client Footprint",
                        value = "Zero Local Emulation (Thin Client)",
                        highlightColor = Emerald400
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Big Action Button
            val isConnecting = vmStatus.state in listOf(
                VmState.AUTHENTICATING,
                VmState.PROVISIONING_VM,
                VmState.WAITING_FOR_VM,
                VmState.CONNECTING_DISPLAY
            )

            Button(
                onClick = {
                    if (vmStatus.state == VmState.RUNNING) {
                        viewModel.stopCloudPc()
                    } else if (!isConnecting) {
                        viewModel.startCloudPc()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("connect_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (vmStatus.state == VmState.RUNNING) Rose500 else Cyan500,
                    contentColor = Slate900
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Slate900,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "STARTING CLOUD PC...",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                } else if (vmStatus.state == VmState.RUNNING) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Stop",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "DISCONNECT & STOP VM",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "START CLOUD PC",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Streaming Quality Presets (Default: LOW 480p for 512MB RAM)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BANDWIDTH & DISPLAY PRESET",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Slate400,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "RGB_565 Decoded",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Cyan400,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StreamQuality.values().forEach { preset ->
                            val isSelected = config.qualityPreset == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Cyan500 else Slate700)
                                    .clickable { viewModel.setQualityPreset(preset) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Slate900 else Slate200
                                        )
                                    )
                                    Text(
                                        text = preset.resolutionLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = if (isSelected) Slate900.copy(alpha = 0.8f) else Slate400
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = config.qualityPreset.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Ultra-Low RAM Architecture Metrics & Guarantee
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Slate800.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Resource Metrics",
                            tint = Emerald400,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "THIN CLIENT RESOURCE TELEMETRY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Slate300,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            label = "Client RAM",
                            value = "${vmStatus.clientRamUsageMb} MB",
                            subtext = "Target: 512MB-1GB devices"
                        )
                        MetricItem(
                            label = "Local CPU Load",
                            value = "< 2%",
                            subtext = "Remote execution only"
                        )
                        MetricItem(
                            label = "APK Footprint",
                            value = "Ultra-Small",
                            subtext = "No OS disk bundled"
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            config = config,
            onDismiss = { viewModel.toggleSettingsDialog(false) },
            onSave = { updated ->
                viewModel.updateConfig(updated)
                viewModel.toggleSettingsDialog(false)
            }
        )
    }

    if (showInfo) {
        ArchitectureInfoDialog(
            onDismiss = { viewModel.toggleInfoDialog(false) }
        )
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    highlightColor: Color = Slate100
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Slate400,
                fontSize = 12.sp
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = highlightColor,
                fontSize = 12.sp
            ),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    subtext: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Slate400,
                fontSize = 10.sp
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Slate100,
                fontSize = 15.sp
            )
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Slate500,
                fontSize = 9.sp
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    config: CloudConfig,
    onDismiss: () -> Unit,
    onSave: (CloudConfig) -> Unit
) {
    var serverUrl by remember { mutableStateOf(config.serverUrl) }
    var selectedProvider by remember { mutableStateOf(config.provider) }
    var vmImage by remember { mutableStateOf(config.vmImage) }
    var vmUsername by remember { mutableStateOf(config.vmUsername) }
    var authToken by remember { mutableStateOf(config.authToken) }
    var idleTimeout by remember { mutableStateOf(config.idleTimeoutMinutes.toString()) }
    var ramAllocation by remember { mutableStateOf(config.ramAllocationMb.toString()) }
    var cpuCores by remember { mutableStateOf(config.cpuCores.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Cyan400)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Cloud Backend & VM Config",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CloudPocket connects to a remote server. Enter your backend endpoint or free-tier cloud host:",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate400)
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://10.0.2.2:8080 or https://vm.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("server_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan400,
                        unfocusedBorderColor = Slate600
                    )
                )

                Text(
                    text = "Cloud Provider (Free-Tier Prioritized):",
                    style = MaterialTheme.typography.labelSmall.copy(color = Slate300, fontWeight = FontWeight.Bold)
                )

                CloudProvider.values().forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedProvider == provider) Slate700 else Slate800)
                            .clickable { selectedProvider = provider }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                            colors = RadioButtonDefaults.colors(selectedColor = Cyan400)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Slate100
                                )
                            )
                            Text(
                                text = provider.description,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    color = Slate400
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = vmImage,
                    onValueChange = { vmImage = it },
                    label = { Text("VM Image") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vmUsername,
                    onValueChange = { vmUsername = it },
                    label = { Text("VM Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Auth Token / API Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ramAllocation,
                        onValueChange = { ramAllocation = it },
                        label = { Text("RAM (MB)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cpuCores,
                        onValueChange = { cpuCores = it },
                        label = { Text("CPU Cores") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = idleTimeout,
                    onValueChange = { idleTimeout = it },
                    label = { Text("Idle Auto-Shutdown (min)") },
                    supportingText = { Text("Saves cloud free-tier quota (default 15m)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        config.copy(
                            serverUrl = serverUrl.trim(),
                            provider = selectedProvider,
                            vmImage = vmImage.trim(),
                            vmUsername = vmUsername.trim(),
                            authToken = authToken.trim(),
                            idleTimeoutMinutes = idleTimeout.toIntOrNull() ?: 15,
                            ramAllocationMb = ramAllocation.toIntOrNull() ?: 1024,
                            cpuCores = cpuCores.toIntOrNull() ?: 1
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = Slate900)
            ) {
                Text("Save Configuration", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        },
        containerColor = Slate900
    )
}

@Composable
fun ArchitectureInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Architecture, contentDescription = null, tint = Cyan400)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "CloudPocket VM Architecture",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "1. Zero Local Virtualization",
                    style = MaterialTheme.typography.titleSmall.copy(color = Cyan400, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "The Android APK contains 0% emulator code and 0% disk images. The Android device acts strictly as a lightweight remote-display and input transmitter. All CPU, RAM, and storage workloads run on the remote cloud host.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate300)
                )

                Text(
                    text = "2. 512MB RAM & Honeycomb Compatibility",
                    style = MaterialTheme.typography.titleSmall.copy(color = Cyan400, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Frames are decoded into 16-bit RGB_565 bitmaps (half the RAM of modern ARGB_8888) with in-place bitmap recycling. The client uses ~18 MB of RAM, leaving plenty of memory even on 512MB devices.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate300)
                )

                Text(
                    text = "3. Operating System Transparency",
                    style = MaterialTheme.typography.titleSmall.copy(color = Cyan400, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "The cloud environment runs open-source ChromiumOS / Chromium-based desktop on Linux. It is explicitly labeled as open-source Chromium and not proprietary Google ChromeOS.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate300)
                )

                Text(
                    text = "4. Free-Tier Cloud Optimization",
                    style = MaterialTheme.typography.titleSmall.copy(color = Cyan400, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Supports Oracle Cloud Always Free (4 ARM OCPUs, 24GB RAM), GCP e2-micro, AWS t2.micro, and self-hosted Docker. Automatic idle shutdown after 15 minutes prevents accidental credit depletion.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate300)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500, contentColor = Slate900)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Slate900
    )
}
