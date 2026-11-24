package com.example.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.NumberFormatters
import com.example.ui.theme.MonoTextStyles
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldTimerCard(
    lastWaterLevelCm: Double?,
    isStabilized: Boolean,
    stabilizationMessage: String,
    onCaptureReading: (elapsedMin: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var targetMinutes by rememberSaveable { mutableIntStateOf(5) }
    var elapsedSeconds by rememberSaveable { mutableLongStateOf(0L) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var alertedForTarget by rememberSaveable { mutableStateOf(false) }

    val targetSeconds = (targetMinutes * 60L).coerceAtLeast(60L)

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000L)
            elapsedSeconds += 1L
            if (elapsedSeconds >= targetSeconds && !alertedForTarget) {
                alertedForTarget = true
                triggerFieldAlert(context)
            }
        }
    }

    val progress = (elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
    val mins = elapsedSeconds / 60L
    val secs = elapsedSeconds % 60L
    val formattedClock = String.format(Locale.US, "%02d:%02d", mins, secs)
    val elapsedMinutesDecimal = (elapsedSeconds / 60.0).coerceAtLeast(0.1)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("field_timer_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "Modo Campo en Vivo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (elapsedSeconds >= targetSeconds) "¡Intervalo de $targetMinutes min cumplido!" else "Meta: $targetMinutes min",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (elapsedSeconds >= targetSeconds) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = formattedClock,
                    style = MonoTextStyles.metricLarge,
                    color = if (elapsedSeconds >= targetSeconds) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("timer_clock_text")
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (elapsedSeconds >= targetSeconds) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )

            // Interval preset chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(1, 2, 5, 10, 15).forEach { preset ->
                    FilterChip(
                        selected = targetMinutes == preset,
                        onClick = {
                            targetMinutes = preset
                            alertedForTarget = elapsedSeconds >= preset * 60L
                        },
                        label = { Text("$preset min", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("preset_${preset}min")
                    )
                }
            }

            // Timer Controls + Capture CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isRunning = !isRunning },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("btn_timer_toggle")
                ) {
                    Icon(
                        if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Pausar cronómetro" else "Iniciar cronómetro",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        isRunning = false
                        elapsedSeconds = 0L
                        alertedForTarget = false
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("btn_timer_reset")
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reiniciar cronómetro")
                }

                Button(
                    onClick = {
                        val capturedMin = if (elapsedSeconds > 0L) elapsedMinutesDecimal else targetMinutes.toDouble()
                        isRunning = false
                        elapsedSeconds = 0L
                        alertedForTarget = false
                        onCaptureReading(capturedMin)
                    },
                    modifier = Modifier.weight(1f).testTag("btn_capture_reading_now")
                ) {
                    Text("Capturar (${if (elapsedSeconds > 0L) NumberFormatters.formatDecimal(elapsedMinutesDecimal, 1) else targetMinutes} min)")
                }
            }

            // Stabilization & Low Water Level indicators
            Surface(
                color = if (isStabilized) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isStabilized) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stabilizationMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isStabilized) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (lastWaterLevelCm != null && lastWaterLevelCm < 4.5) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Nivel bajo (${NumberFormatters.formatDecimal(lastWaterLevelCm, 1)} cm): considera recargar agua y registrar Δhᵣ en la siguiente lectura.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

private fun triggerFieldAlert(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(320L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(320L)
        }
    }
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
    }
}
