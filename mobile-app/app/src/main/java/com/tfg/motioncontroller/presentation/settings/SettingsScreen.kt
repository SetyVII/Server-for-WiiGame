package com.tfg.motioncontroller.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tfg.motioncontroller.domain.model.GameSettings
import com.tfg.motioncontroller.domain.model.SensitivityLevel

/************************************************************************************
 * SettingsScreen: permite al usuario personalizar su experiencia.
 * Desde aquí se controla el aspecto visual, el método de control (táctil vs botones)
 * y la "finura" con la que responde el movimiento.
 ************************************************************************************/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Obtenemos los ajustes actuales. Si no hay, usamos los de por defecto.
    val settings by viewModel.settings.collectAsState(initial = com.tfg.motioncontroller.domain.model.GameSettings())

    // Titulo y "volver".
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECCIÓN: APARIENCIA ---
            // Permite alternar entre modo claro y oscuro.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Apariencia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botón Modo Claro
                        Button(
                            onClick = { viewModel.saveSettings(settings.copy(darkMode = false)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!settings.darkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Claro")
                        }
                        // Botón Modo Oscuro
                        Button(
                            onClick = { viewModel.saveSettings(settings.copy(darkMode = true)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.darkMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Oscuro")
                        }
                    }
                }
            }

            // --- SECCIÓN: MODO DE CONTROL ---
            // Define si el usuario prefiere inclinar el móvil (Touchpad/Giroscopio) o usar botones clásicos.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Modo de Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón Touchpad/Giroscopio
                        Button(
                            onClick = { viewModel.saveSettings(settings.copy(controlMode = com.tfg.motioncontroller.domain.model.ControlMode.TOUCHPAD)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.controlMode == com.tfg.motioncontroller.domain.model.ControlMode.TOUCHPAD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) { Text("Touchpad") }
                        
                        // Botón de Controles Clásicos
                        Button(
                            onClick = { viewModel.saveSettings(settings.copy(controlMode = com.tfg.motioncontroller.domain.model.ControlMode.BUTTONS)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.controlMode == com.tfg.motioncontroller.domain.model.ControlMode.BUTTONS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) { Text("Botones") }
                    }
                }
            }

            // --- SECCIÓN: SENSIBILIDAD ---
            // Ajusta qué tan "fuerte" se mueve el personaje en Unity al inclinar el móvil.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Sensibilidad del control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    SensitivityGrid(
                        selected = settings.sensitivity,
                        onSelect = { viewModel.saveSettings(settings.copy(sensitivity = it)) }
                    )

                    // Si el usuario elige "Personalizado", permitimos escribir un valor numérico exacto.
                    if (settings.sensitivity == SensitivityLevel.CUSTOM) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Fuerza personalizada (1-100):", style = MaterialTheme.typography.bodyMedium)
                        TextField(
                            value = settings.customForce.toString(),
                            onValueChange = { value ->
                                val force = value.toIntOrNull() ?: 45
                                viewModel.saveSettings(settings.copy(customForce = force.coerceIn(1, 100)))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }

                    // Resumen informativo de la configuración actual.
                    CurrentSensitivitySummary(settings)
                }
            }
        }
    }
}

// Muestra un pequeño cuadro con el nivel y la fuerza numérica aplicada.
@Composable
private fun CurrentSensitivitySummary(settings: GameSettings) {
    val currentForce = when (settings.sensitivity) {
        SensitivityLevel.LOW -> 8
        SensitivityLevel.MEDIUM -> 45
        SensitivityLevel.HIGH -> 100
        SensitivityLevel.CUSTOM -> settings.customForce
    }
    
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Nivel:", style = MaterialTheme.typography.bodyMedium)
                Text(settings.sensitivity.label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Fuerza:", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (settings.sensitivity == SensitivityLevel.CUSTOM) "${(currentForce / 10.0).format(1)}" else settings.sensitivity.description,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Cuadrícula de opciones para seleccionar la sensibilidad.
@Composable
private fun SensitivityGrid(
    selected: SensitivityLevel,
    onSelect: (SensitivityLevel) -> Unit
) {
    val options = listOf(
        SensitivityLevel.LOW to ">",
        SensitivityLevel.MEDIUM to ">>",
        SensitivityLevel.HIGH to ">>>",
        SensitivityLevel.CUSTOM to "⚙"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { (level, icon) ->
                    val isSelected = selected == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                } else {
                                    MaterialTheme.colorScheme.background
                                }
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelect(level) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.headlineSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = level.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
