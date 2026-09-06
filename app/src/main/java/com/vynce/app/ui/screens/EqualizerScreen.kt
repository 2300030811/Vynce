/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

val BANDS = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
val PRESETS = mapOf(
    "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Rock" to listOf(4f, 3f, -1f, -2f, 0f, 2f, 4f, 5f, 4f, 3f),
    "Pop" to listOf(-1f, 2f, 4f, 5f, 3f, 0f, -1f, -2f, 1f, 2f),
    "Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
    "Electronic" to listOf(5f, 4f, 2f, 0f, -2f, 2f, 1f, 3f, 4f, 5f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var enabled by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf("Flat") }
    val bandLevels = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) }
    var bassBoost by remember { mutableFloatStateOf(0f) }
    var virtualizer by remember { mutableFloatStateOf(0f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Equalizer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("EQ", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Frequency Curve Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (bandLevels.isEmpty()) return@Canvas
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    val points = bandLevels.mapIndexed { index, level ->
                        val x = (index.toFloat() / (bandLevels.size - 1)) * width
                        val y = centerY - (level / 12f) * (height / 2f)
                        Offset(x, y)
                    }

                    val path = Path().apply {
                        if (points.isNotEmpty()) {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val controlX1 = prev.x + (curr.x - prev.x) / 2f
                                cubicTo(controlX1, prev.y, controlX1, curr.y, curr.x, curr.y)
                            }
                        }
                    }

                    drawPath(
                        path = path,
                        color = if (enabled) primaryColor else Color.Gray,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets Horizontal Row
            Text("Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESETS.keys.toList()) { preset ->
                    FilterChip(
                        selected = selectedPreset == preset,
                        onClick = {
                            selectedPreset = preset
                            PRESETS[preset]?.let { levels ->
                                levels.forEachIndexed { i, l -> bandLevels[i] = l }
                            }
                        },
                        label = { Text(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sound FX Sliders (Bass Boost & Surround)
            Text("Sound FX", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Bass Boost: ${(bassBoost * 100).toInt()}%", fontSize = 12.sp)
                    Slider(
                        value = bassBoost,
                        onValueChange = { bassBoost = it },
                        enabled = enabled
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Surround: ${(virtualizer * 100).toInt()}%", fontSize = 12.sp)
                    Slider(
                        value = virtualizer,
                        onValueChange = { virtualizer = it },
                        enabled = enabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 10-Band Sliders Grid
            Text("Frequency Bands", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            BANDS.forEachIndexed { index, freq ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Text(
                        text = freq,
                        modifier = Modifier.width(60.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = bandLevels[index],
                        onValueChange = { newValue ->
                            bandLevels[index] = newValue
                            selectedPreset = "Custom"
                        },
                        valueRange = -12f..12f,
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${if (bandLevels[index] > 0) "+" else ""}${bandLevels[index].toInt()} dB",
                        modifier = Modifier.width(50.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
