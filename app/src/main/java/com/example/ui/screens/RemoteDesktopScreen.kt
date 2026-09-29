package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CloudConfig
import com.example.model.StreamQuality
import com.example.model.VmState
import com.example.model.VmStatus
import com.example.ui.theme.*
import com.example.viewmodel.CloudPocketViewModel

@Composable
fun RemoteDesktopScreen(
    viewModel: CloudPocketViewModel,
    modifier: Modifier = Modifier
) {
    val vmStatus by viewModel.vmStatus.collectAsState()
    val config by viewModel.config.collectAsState()
    val currentFrame by viewModel.currentFrame.collectAsState()
    val isKeyboardOpen by viewModel.isKeyboardVisible.collectAsState()
    val isMousePointerMode by viewModel.isMousePointerMode.collectAsState()
    val pointerPosition by viewModel.pointerPosition.collectAsState()

    var showDisconnectDialog by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var inputText by remember { mutableStateOf("") }

    // Intercept Android hardware Back button
    BackHandler {
        showDisconnectDialog = true
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Isolated High-Performance Remote Display Canvas
        RemoteDisplaySurface(
            frameProvider = { currentFrame },
            isMousePointerMode = isMousePointerMode,
            pointerPositionProvider = { pointerPosition },
            onTouchDown = viewModel::onTouchDown,
            onTouchMove = viewModel::onTouchMove,
            onTouchUp = viewModel::onTouchUp,
            onLongPress = viewModel::onLongPress,
            resolutionLabel = config.qualityPreset.resolutionLabel
        )

        // 2. Isolated Top Performance HUD (Only updates on 1Hz metrics tick)
        RemoteDesktopHUD(
            statusProvider = { vmStatus },
            qualityPreset = config.qualityPreset,
            onQualityToggle = {
                val next = when (config.qualityPreset) {
                    StreamQuality.LOW -> StreamQuality.BALANCED
                    StreamQuality.BALANCED -> StreamQuality.QUALITY
                    StreamQuality.QUALITY -> StreamQuality.LOW
                }
                viewModel.setQualityPreset(next)
            }
        )

        // 3. Status Alerts (Reconnecting or Idle warnings)
        if (vmStatus.state == VmState.RECONNECTING) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                color = Amber500.copy(alpha = 0.95f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Slate900,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reconnecting to cloud computer...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Slate900,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        if (vmStatus.isIdleWarning) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 95.dp),
                color = Amber400,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Idle auto-shutdown in ${vmStatus.idleRemainingSeconds}s (saving free-tier quota). Tap to stay active.",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Slate900,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // 4. Hidden Input Relay for Android Software Keyboard
        BasicTextField(
            value = inputText,
            onValueChange = { newText ->
                if (newText.length > inputText.length) {
                    val typed = newText.substring(inputText.length)
                    typed.forEach { char ->
                        viewModel.sendKeyPress(char.toString(), char.code)
                    }
                } else if (newText.length < inputText.length) {
                    viewModel.sendSpecialKey("BACKSPACE")
                }
                inputText = newText
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                viewModel.sendSpecialKey("ENTER")
            }),
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester)
        )

        // 5. Isolated Bottom Controls & Keyboard Bar
        RemoteControlsContainer(
            modifier = Modifier.align(Alignment.BottomCenter),
            isKeyboardOpen = isKeyboardOpen,
            isMousePointerMode = isMousePointerMode,
            onToggleMouseMode = viewModel::toggleMousePointerMode,
            onToggleKeyboard = viewModel::toggleKeyboard,
            onSendSpecialKey = viewModel::sendSpecialKey,
            onDisconnectClick = { showDisconnectDialog = true }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = {
                Text(
                    text = "Cloud Computer Session",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Do you want to disconnect your display session (keeps VM running in cloud) or completely shut down the cloud VM to save free-tier hours?",
                    style = MaterialTheme.typography.bodySmall.copy(color = Slate300)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.stopCloudPc()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500, contentColor = Slate900)
                ) {
                    Text("Stop VM & Free Resources", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnectSessionOnly()
                    }
                ) {
                    Text("Disconnect Only", color = Cyan400)
                }
            },
            containerColor = Slate900
        )
    }
}

@Composable
private fun RemoteDisplaySurface(
    frameProvider: () -> Bitmap?,
    isMousePointerMode: Boolean,
    pointerPositionProvider: () -> Pair<Float, Float>,
    onTouchDown: (Float, Float) -> Unit,
    onTouchMove: (Float, Float) -> Unit,
    onTouchUp: (Float, Float) -> Unit,
    onLongPress: (Float, Float) -> Unit,
    resolutionLabel: String
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val frame = frameProvider()
    if (frame != null) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("remote_display_canvas")
                .onGloballyPositioned { canvasSize = it.size }
                .pointerInput(isMousePointerMode) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val xRatio = (offset.x / w).coerceIn(0f, 1f)
                            val yRatio = (offset.y / h).coerceIn(0f, 1f)
                            onTouchDown(xRatio, yRatio)
                            onTouchUp(xRatio, yRatio)
                        },
                        onLongPress = { offset ->
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val xRatio = (offset.x / w).coerceIn(0f, 1f)
                            val yRatio = (offset.y / h).coerceIn(0f, 1f)
                            onLongPress(xRatio, yRatio)
                        }
                    )
                }
                .pointerInput(isMousePointerMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            val xRatio = (offset.x / w).coerceIn(0f, 1f)
                            val yRatio = (offset.y / h).coerceIn(0f, 1f)
                            onTouchDown(xRatio, yRatio)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val w = canvasSize.width.coerceAtLeast(1).toFloat()
                            val h = canvasSize.height.coerceAtLeast(1).toFloat()
                            if (isMousePointerMode) {
                                val (currX, currY) = pointerPositionProvider()
                                val dx = dragAmount.x / w
                                val dy = dragAmount.y / h
                                onTouchMove((currX + dx).coerceIn(0f, 1f), (currY + dy).coerceIn(0f, 1f))
                            } else {
                                val xRatio = (change.position.x / w).coerceIn(0f, 1f)
                                val yRatio = (change.position.y / h).coerceIn(0f, 1f)
                                onTouchMove(xRatio, yRatio)
                            }
                        },
                        onDragEnd = {
                            val (xRatio, yRatio) = pointerPositionProvider()
                            onTouchUp(xRatio, yRatio)
                        }
                    )
                }
        ) {
            val currentBitmap = frameProvider()
            if (currentBitmap != null && !currentBitmap.isRecycled) {
                val imageBitmap = currentBitmap.asImageBitmap()
                val srcW = imageBitmap.width.toFloat()
                val srcH = imageBitmap.height.toFloat()
                val canvasW = size.width
                val canvasH = size.height

                val scale = minOf(canvasW / srcW, canvasH / srcH)
                val dstW = (srcW * scale).toInt()
                val dstH = (srcH * scale).toInt()
                val dstX = ((canvasW - dstW) / 2f).toInt()
                val dstY = ((canvasH - dstH) / 2f).toInt()

                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(dstX, dstY),
                    dstSize = IntSize(dstW, dstH),
                    filterQuality = FilterQuality.Low
                )

                if (isMousePointerMode) {
                    val (px, py) = pointerPositionProvider()
                    val cx = dstX + px * dstW
                    val cy = dstY + py * dstH
                    drawCircle(
                        color = Cyan400.copy(alpha = 0.5f),
                        radius = 8.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Cyan400)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Receiving cloud display stream...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Slate200)
                )
                Text(
                    text = "Negotiating low-bandwidth $resolutionLabel stream",
                    style = MaterialTheme.typography.labelSmall.copy(color = Slate400)
                )
            }
        }
    }
}

@Composable
private fun RemoteDesktopHUD(
    statusProvider: () -> VmStatus,
    qualityPreset: StreamQuality,
    onQualityToggle: () -> Unit
) {
    val status = statusProvider()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Slate900.copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (status.state == VmState.RUNNING) Emerald400 else Amber400)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${qualityPreset.resolutionLabel} | ${status.fps} FPS | ${status.latencyMs}ms | RAM: ${status.clientRamUsageMb}MB",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Slate200,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }

        Surface(
            color = Slate800.copy(alpha = 0.85f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.testTag("quality_toggle_button")
        ) {
            TextButton(
                onClick = onQualityToggle,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = qualityPreset.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Cyan400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun RemoteControlsContainer(
    modifier: Modifier = Modifier,
    isKeyboardOpen: Boolean,
    isMousePointerMode: Boolean,
    onToggleMouseMode: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onSendSpecialKey: (String) -> Unit,
    onDisconnectClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(
            visible = isKeyboardOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Surface(
                color = Slate900.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickKeyButton("Esc") { onSendSpecialKey("ESC") }
                    QuickKeyButton("Tab") { onSendSpecialKey("TAB") }
                    QuickKeyButton("Ctrl+T") { onSendSpecialKey("CTRL_T") }
                    QuickKeyButton("Ctrl+W") { onSendSpecialKey("CTRL_W") }
                    QuickKeyButton("Search") { onSendSpecialKey("SEARCH") }
                    QuickKeyButton("Enter") { onSendSpecialKey("ENTER") }
                    QuickKeyButton("⌫") { onSendSpecialKey("BACKSPACE") }
                }
            }
        }

        Surface(
            color = Slate900.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleMouseMode,
                    modifier = Modifier.testTag("mouse_mode_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isMousePointerMode) Icons.Default.Mouse else Icons.Default.TouchApp,
                            contentDescription = "Mouse Mode",
                            tint = if (isMousePointerMode) Cyan400 else Slate400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isMousePointerMode) "Trackpad" else "Touch",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = if (isMousePointerMode) Cyan400 else Slate400
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onToggleKeyboard,
                    modifier = Modifier.testTag("keyboard_toggle_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Keyboard",
                            tint = if (isKeyboardOpen) Cyan400 else Slate400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Keyboard",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = if (isKeyboardOpen) Cyan400 else Slate400
                            )
                        )
                    }
                }

                IconButton(
                    onClick = { onSendSpecialKey("BACK") },
                    modifier = Modifier.testTag("remote_back_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate300,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = Slate400
                            )
                        )
                    }
                }

                IconButton(
                    onClick = { onSendSpecialKey("HOME") },
                    modifier = Modifier.testTag("remote_home_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = Slate300,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = Slate400
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDisconnectClick,
                    modifier = Modifier.testTag("disconnect_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
                            tint = Rose400,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Disconnect",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                color = Rose400
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickKeyButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Slate800)
            .border(1.dp, Slate700, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Slate200,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
