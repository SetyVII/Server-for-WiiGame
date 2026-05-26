package com.tfg.motioncontroller.presentation.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tfg.motioncontroller.domain.model.SocketState
import com.tfg.motioncontroller.ui.theme.ButtonA
import com.tfg.motioncontroller.ui.theme.ButtonB
import com.tfg.motioncontroller.ui.theme.DPadBackground
import com.tfg.motioncontroller.ui.theme.DPadBorder
import com.tfg.motioncontroller.ui.theme.DPadDot
import com.tfg.motioncontroller.ui.theme.TopBarBackground
import com.tfg.motioncontroller.ui.theme.VolumeBarGradientEnd
import com.tfg.motioncontroller.ui.theme.VolumeBarGradientMid
import com.tfg.motioncontroller.ui.theme.VolumeBarGradientStart

@Composable
fun ControllerScreen(
    onSettingsClick: () -> Unit,
    onDisconnect: () -> Unit,
    viewModel: ControllerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Mostrar Snackbar cuando hay mensaje de snackbarMessage
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    // Cuenta atras para volver al menu cuando se pierde la conexion
    LaunchedEffect(uiState.reconnectCountdown) {
        uiState.reconnectCountdown?.let { count ->
            if (count == 0) {
                viewModel.disconnect()
                onDisconnect()
            }
        }
    }

    // Modo inmersivo: ocultar barra de navegacion y notificaciones
    DisposableEffect(Unit) {
        val activity = context as? ComponentActivity
        val window = activity?.window
        val insetsController = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Lanzador para solicitar permiso de microfono
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startMicrophone()
        } else {
            viewModel.showPermissionDenied()
        }
    }

    // Funcion para verificar y solicitar permiso de microfono
    val checkAndRequestMicPermission = {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.startMicrophone()
            }
            else -> {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Barra superior
        TopBar(
            socketState = uiState.connectionStatus.socketState,
            playerId = uiState.playerId,
            micActive = uiState.microphoneState.isActive,
            sensorsActive = uiState.sensorsActive,
            onToggleSensors = {
                if (uiState.sensorsActive) {
                    viewModel.stopSensors()
                } else {
                    viewModel.startSensors()
                }
            },
            onToggleMic = {
                if (uiState.microphoneState.isActive) {
                    viewModel.stopMicrophone()
                } else {
                    checkAndRequestMicPermission()
                }
            },
            onTestVibration = { viewModel.testVibration() },
            onSettingsClick = onSettingsClick,
            onDisconnect = {
                viewModel.disconnect()
                onDisconnect()
            }
        )

        // Indicador de calibracion
        if (uiState.isCalibrating) {
            CalibrationIndicator(
                progress = uiState.calibrationProgress,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Panel de debug (valores de sensores)
        if (uiState.sensorsActive && !uiState.isCalibrating) {
            SensorDebugInfo(
                tiltX = uiState.sensorValues.tiltX,
                tiltY = uiState.sensorValues.tiltY,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Panel de microfono expandible
        AnimatedVisibility(
            visible = uiState.microphoneState.isActive,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            MicPanel(
                rmsLevel = uiState.microphoneState.rmsLevel,
                isBlowing = uiState.microphoneState.isBlowing,
                threshold = uiState.microphoneState.threshold,
                cooldown = uiState.microphoneState.cooldown,
                scale = uiState.microphoneState.scale,
                onThresholdChange = { viewModel.updateMicrophoneSettings(it, uiState.microphoneState.cooldown, uiState.microphoneState.scale) },
                onCooldownChange = { viewModel.updateMicrophoneSettings(uiState.microphoneState.threshold, it, uiState.microphoneState.scale) },
                onScaleChange = { viewModel.updateMicrophoneSettings(uiState.microphoneState.threshold, uiState.microphoneState.cooldown, it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Area principal del mando
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Control area (Touchpad o Botones)
                if (uiState.settings.controlMode == com.tfg.motioncontroller.domain.model.ControlMode.TOUCHPAD) {
                    DPad(
                        tiltX = uiState.sensorValues.gamma,
                        tiltY = uiState.sensorValues.beta,
                        isCalibrating = uiState.isCalibrating,
                        sensorsActive = uiState.sensorsActive,
                        onTiltChange = { gamma, beta -> viewModel.setManualTilt(gamma, beta) },
                        onTiltReset = { viewModel.resetManualTilt() },
                        modifier = Modifier
                            .width(380.dp)
                            .height(250.dp)
                    )
                } else {
                    ButtonsPad(
                        onButtonPress = { x, y -> viewModel.setDPadButton(x, y) },
                        onButtonRelease = { viewModel.resetDPadButton() },
                        modifier = Modifier
                            .width(380.dp)
                            .height(250.dp)
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))

                // Botones A y B (derecha)
                ActionButtons(
                    onButtonAPress = { viewModel.setButtonA(true) },
                    onButtonARelease = { viewModel.setButtonA(false) },
                    onButtonBPress = { viewModel.setButtonB(true) },
                    onButtonBRelease = { viewModel.setButtonB(false) },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // Contador de pickups flotante
            PickupCounter(
                count = uiState.pickupCount,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // Mensaje de error
        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        // Log de eventos del juego
        uiState.logMessage?.let { log ->
            Text(
                text = log,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
}

@Composable
private fun CalibrationIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Calibrando sensores...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${(progress * 100).toInt()}% - Manten el movil quieto",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopBar(
    socketState: SocketState,
    playerId: Int?,
    micActive: Boolean,
    sensorsActive: Boolean,
    onToggleSensors: () -> Unit,
    onToggleMic: () -> Unit,
    onTestVibration: () -> Unit,
    onSettingsClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val socketStatusText = when (socketState) {
        SocketState.CONNECTED -> "Socket: conectado"
        SocketState.CONNECTING -> "Socket: conectando..."
        SocketState.ERROR -> "Socket: error"
        SocketState.DISCONNECTED -> "Socket: desconectado"
    }

    val playerStatusText = playerId?.let { "Jugador $it" } ?: "Sin rol"
    val playerColor = when (playerId) {
        1 -> Color(0xFF00A8FF)
        2 -> Color(0xFFE84118)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val socketStatusColor = if (socketState == SocketState.CONNECTED) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TopBarBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = socketStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = socketStatusColor
            )
            Text(
                text = playerStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = playerColor,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onToggleSensors,
            colors = if (sensorsActive) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                if (sensorsActive) "Desactivar sensores" else "Activar sensores",
                style = MaterialTheme.typography.labelSmall
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleMic) {
                Icon(
                    imageVector = if (micActive) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = if (micActive) "Desactivar microfono" else "Activar microfono",
                    tint = if (micActive) MaterialTheme.colorScheme.error else iconColor
                )
            }

            IconButton(onClick = onTestVibration) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = "Test de vibracion",
                    tint = iconColor
                )
            }

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuracion",
                    tint = iconColor
                )
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Desconectar", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DPad(
    tiltX: Float,
    tiltY: Float,
    isCalibrating: Boolean,
    sensorsActive: Boolean,
    onTiltChange: (Float, Float) -> Unit,
    onTiltReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Radio maximo para movimiento de la bolita (proporcional al tamano)
    // Usamos 40% del tamano disponible como radio maximo
    val density = LocalDensity.current
    var maxRadiusXPx by remember { mutableStateOf(0f) }
    var maxRadiusYPx by remember { mutableStateOf(0f) }
    
    var manualOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    // Offset visual: usa manualOffset si se arrastra, sino usa valores del sensor
    val visualOffset = if (isDragging) {
        manualOffset
    } else {
        Offset(tiltX * maxRadiusXPx, tiltY * maxRadiusYPx)
    }

    // Color de la bolita: apagado cuando sensores activos
    val dotColor = when {
        sensorsActive -> Color.Gray
        isCalibrating -> Color.Gray
        else -> DPadDot
    }

    var dpadWidth by remember { mutableStateOf(0f) }
    var dpadHeight by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                dpadWidth = size.width.toFloat()
                dpadHeight = size.height.toFloat()
                maxRadiusXPx = size.width / 2f * 0.75f
                maxRadiusYPx = size.height / 2f * 0.75f
            }
            .clip(RoundedCornerShape(percent = 50)) // Forma ovalada
            .background(DPadBackground.copy(alpha = if (sensorsActive) 0.5f else 1f))
            .padding(4.dp)
            .pointerInput(sensorsActive) {
                if (sensorsActive) return@pointerInput
                
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        // Calcular offset inicial relativo al centro
                        manualOffset = Offset(
                            offset.x - dpadWidth / 2f,
                            offset.y - dpadHeight / 2f
                        )
                        // Limitar al radio ovalado
                        val normalizedX = manualOffset.x / maxRadiusXPx
                        val normalizedY = manualOffset.y / maxRadiusYPx
                        val dist = kotlin.math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY)
                        if (dist > 1f) {
                            manualOffset = Offset(
                                manualOffset.x / dist,
                                manualOffset.y / dist
                            )
                        }
                        // Calcular gamma/beta
                        val gamma = (manualOffset.x / maxRadiusXPx).coerceIn(-1f, 1f)
                        val beta = -(manualOffset.y / maxRadiusYPx).coerceIn(-1f, 1f)
                        onTiltChange(gamma, beta)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = manualOffset + Offset(dragAmount.x, dragAmount.y)
                        val normalizedX = newOffset.x / maxRadiusXPx
                        val normalizedY = newOffset.y / maxRadiusYPx
                        val dist = kotlin.math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY)
                        manualOffset = if (dist > 1f) {
                            Offset(
                                newOffset.x / dist,
                                newOffset.y / dist
                            )
                        } else {
                            newOffset
                        }
                        val gamma = (manualOffset.x / maxRadiusXPx).coerceIn(-1f, 1f)
                        val beta = -(manualOffset.y / maxRadiusYPx).coerceIn(-1f, 1f)
                        onTiltChange(gamma, beta)
                    },
                    onDragEnd = {
                        isDragging = false
                        manualOffset = Offset.Zero
                        onTiltReset()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Borde decorativo
        val borderIntensity = if (isDragging) {
            val normalizedX = manualOffset.x / maxRadiusXPx
            val normalizedY = manualOffset.y / maxRadiusYPx
            kotlin.math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY).coerceIn(0f, 1f)
        } else {
            ((kotlin.math.abs(tiltX) + kotlin.math.abs(tiltY)) / 2f).coerceIn(0f, 1f)
        }
        val borderColor = androidx.compose.ui.graphics.lerp(
            DPadBorder,
            Color(0xFFFF6B6B),
            borderIntensity
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(percent = 50))
                .background(DPadBackground)
                .padding(2.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(borderColor.copy(alpha = 0.3f))
        )

        // Bolita del D-Pad (48.dp)
        val dotOffsetX = with(density) { visualOffset.x.toDp() }
        val dotOffsetY = with(density) { visualOffset.y.toDp() }

        Box(
            modifier = Modifier
                .size(48.dp)
                .offset(x = dotOffsetX, y = dotOffsetY)
                .clip(CircleShape)
                .background(dotColor)
        )
    }
}

@Composable
private fun ButtonsPad(
    onButtonPress: (Int, Int) -> Unit,
    onButtonRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttons = listOf(
        Triple("W", 0, 1),
        Triple("A", -1, 0),
        Triple("D", 1, 0),
        Triple("S", 0, -1)
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Fila superior: W
            DPadButton(
                text = "W",
                onPress = { onButtonPress(0, 1) },
                onRelease = onButtonRelease
            )
            // Fila media: A, espacio, D
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DPadButton(
                    text = "A",
                    onPress = { onButtonPress(-1, 0) },
                    onRelease = onButtonRelease
                )
                Spacer(modifier = Modifier.size(80.dp))
                DPadButton(
                    text = "D",
                    onPress = { onButtonPress(1, 0) },
                    onRelease = onButtonRelease
                )
            }
            // Fila inferior: S
            DPadButton(
                text = "S",
                onPress = { onButtonPress(0, -1) },
                onRelease = onButtonRelease
            )
        }
    }
}

@Composable
private fun DPadButton(
    text: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onPress()
        } else {
            onRelease()
        }
    }

    Button(
        onClick = { /* handled by interactionSource */ },
        modifier = modifier.size(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DPadBackground,
            disabledContainerColor = Color(0xFF333333)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = DPadBorder
        ),
        interactionSource = interactionSource
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MicPanel(
    rmsLevel: Float,
    isBlowing: Boolean,
    threshold: Float,
    cooldown: Int,
    scale: Float,
    onThresholdChange: (Float) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                CircleShape.copy(all = androidx.compose.foundation.shape.CornerSize(8.dp))
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Microfono",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val progress = (rmsLevel * 100f * scale).coerceIn(0f, 100f) / 100f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = when {
                        progress > 0.8f -> VolumeBarGradientEnd
                        progress > 0.5f -> VolumeBarGradientMid
                        else -> VolumeBarGradientStart
                    },
                    trackColor = Color.Transparent
                )

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(rmsLevel * 100).toInt()}% RMS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp)
            )
        }

        AnimatedVisibility(visible = isBlowing) {
            Text(
                text = "SOPLO! (${(rmsLevel * 100).toInt()}%)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sensib.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = threshold * 100,
                    onValueChange = { onThresholdChange(it / 100f) },
                    valueRange = 5f..50f,
                    modifier = Modifier.height(24.dp)
                )
                Text(
                    text = "%.2f".format(threshold),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cooldown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = cooldown.toFloat(),
                    onValueChange = { onCooldownChange(it.toInt()) },
                    valueRange = 200f..2000f,
                    modifier = Modifier.height(24.dp)
                )
                Text(
                    text = "${cooldown}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Escala",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = scale,
                    onValueChange = { onScaleChange(it) },
                    valueRange = 1f..10f,
                    modifier = Modifier.height(24.dp)
                )
                Text(
                    text = "%.1fx".format(scale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onButtonAPress: () -> Unit,
    onButtonARelease: () -> Unit,
    onButtonBPress: () -> Unit,
    onButtonBRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            PressableButton(
                text = "A",
                subtext = "SALTAR",
                onPress = onButtonAPress,
                onRelease = onButtonARelease,
                color = ButtonA,
                modifier = Modifier.size(120.dp)
            )

            PressableButton(
                text = "B",
                subtext = "VALIDAR",
                onPress = onButtonBPress,
                onRelease = onButtonBRelease,
                color = ButtonB,
                modifier = Modifier.size(120.dp)
            )
        }
    }
}

@Composable
private fun PressableButton(
    text: String,
    subtext: String,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onPress()
        } else {
            onRelease()
        }
    }

    Button(
        onClick = { /* handled by interactionSource */ },
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        interactionSource = interactionSource
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.headlineMedium)
            Text(subtext, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SensorDebugInfo(
    tiltX: Float,
    tiltY: Float,
    modifier: Modifier = Modifier
) {
    Text(
        text = "tiltX: ${tiltX.format(2)} | tiltY: ${tiltY.format(2)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PickupCounter(
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color(0xFF1A1A2E),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 2.dp,
                color = Color(0xFFFFD700),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AttachMoney,
            contentDescription = "Monedas",
            tint = Color(0xFFFFD700),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = count.toString(),
            color = Color(0xFFFFD700),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
