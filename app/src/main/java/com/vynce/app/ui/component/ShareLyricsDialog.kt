package com.vynce.app.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynce.app.models.MediaMetadata
import com.vynce.app.ui.theme.VyncePurple
import com.vynce.app.utils.ComposeToImage

data class LyricCardTheme(
    val name: String,
    val bgColors: List<Color>,
    val textColor: Color,
    val accentColor: Color,
    val startArgb: Int,
    val endArgb: Int,
    val textArgb: Int,
    val accentArgb: Int
)

val CardThemes = listOf(
    LyricCardTheme("Vynce Purple", listOf(Color(0xFF2E0854), Color(0xFF0F051D)), Color.White, Color(0xFFC084FC), 0xFF2E0854.toInt(), 0xFF0F051D.toInt(), 0xFFFFFFFF.toInt(), 0xFFC084FC.toInt()),
    LyricCardTheme("Midnight Blue", listOf(Color(0xFF0F172A), Color(0xFF020617)), Color.White, Color(0xFF38BDF8), 0xFF0F172A.toInt(), 0xFF020617.toInt(), 0xFFFFFFFF.toInt(), 0xFF38BDF8.toInt()),
    LyricCardTheme("Emerald Aurora", listOf(Color(0xFF064E3B), Color(0xFF022C22)), Color.White, Color(0xFF34D399), 0xFF064E3B.toInt(), 0xFF022C22.toInt(), 0xFFFFFFFF.toInt(), 0xFF34D399.toInt()),
    LyricCardTheme("Sunset Fire", listOf(Color(0xFF7C2D12), Color(0xFF431407)), Color.White, Color(0xFFFB923C), 0xFF7C2D12.toInt(), 0xFF431407.toInt(), 0xFFFFFFFF.toInt(), 0xFFFB923C.toInt()),
    LyricCardTheme("Rose Velvet", listOf(Color(0xFF831843), Color(0xFF4C0519)), Color.White, Color(0xFFF472B6), 0xFF831843.toInt(), 0xFF4C0519.toInt(), 0xFFFFFFFF.toInt(), 0xFFF472B6.toInt()),
    LyricCardTheme("Pure AMOLED", listOf(Color(0xFF18181B), Color(0xFF09090B)), Color.White, Color(0xFFA1A1AA), 0xFF18181B.toInt(), 0xFF09090B.toInt(), 0xFFFFFFFF.toInt(), 0xFFA1A1AA.toInt())
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLyricsDialog(
    lyricsText: String,
    songTitle: String,
    artistName: String,
    mediaMetadata: MediaMetadata?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val lines = remember(lyricsText) {
        lyricsText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") }
    }

    var selectedIndices by remember {
        mutableStateOf(
            if (lines.isNotEmpty()) setOf(0, 1.coerceAtMost(lines.lastIndex)) else emptySet()
        )
    }
    var selectedTheme by remember { mutableStateOf(CardThemes.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Share Lyrics Card",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Select lines to include in your shareable quote card",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Card Preview
            val selectedText = selectedIndices.sorted().mapNotNull { lines.getOrNull(it) }.joinToString("\n")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(selectedTheme.bgColors))
                        .padding(24.dp)
                ) {
                    Column {
                        Surface(
                            color = selectedTheme.accentColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "VYNCE MUSIC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = selectedTheme.accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = selectedText.ifEmpty { "Tap lines below to select lyrics" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = selectedTheme.textColor,
                            lineHeight = 26.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(
                                    text = songTitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedTheme.textColor
                                )
                                Text(
                                    text = artistName,
                                    fontSize = 12.sp,
                                    color = selectedTheme.accentColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Theme selector
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(CardThemes) { theme ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(theme.bgColors))
                            .border(
                                width = if (selectedTheme == theme) 2.dp else 1.dp,
                                color = if (selectedTheme == theme) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedTheme = theme }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Line selector list
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                lines.forEachIndexed { index, line ->
                    val isSelected = selectedIndices.contains(index)
                    Surface(
                        onClick = {
                            selectedIndices = if (isSelected) {
                                selectedIndices - index
                            } else {
                                if (selectedIndices.size < 6) selectedIndices + index else selectedIndices
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) VyncePurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, VyncePurple) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) VyncePurple else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Share Button
            Button(
                onClick = {
                    val bitmap = renderLyricsCardToBitmap(
                        selectedLines = selectedIndices.sorted().mapNotNull { lines.getOrNull(it) },
                        title = songTitle,
                        artist = artistName,
                        theme = selectedTheme
                    )
                    ComposeToImage.shareBitmap(context, bitmap, "Share Lyrics Card")
                    onDismiss()
                },
                enabled = selectedIndices.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VyncePurple)
            ) {
                Icon(Icons.Rounded.Share, null)
                Spacer(Modifier.width(8.dp))
                Text("Share Card as Image / Story", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun renderLyricsCardToBitmap(
    selectedLines: List<String>,
    title: String,
    artist: String,
    theme: LyricCardTheme
): Bitmap {
    val width = 1080
    val height = 1350 // 4:5 Instagram Story / Post format
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Background Gradient
    val gradientPaint = Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            theme.startArgb, theme.endArgb,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)

    // Brand Badge Pill
    val badgePaint = Paint().apply {
        color = theme.accentArgb
        alpha = 60
        style = Paint.Style.FILL
    }
    val badgeRect = RectF(80f, 100f, 320f, 160f)
    canvas.drawRoundRect(badgeRect, 20f, 20f, badgePaint)

    val badgeTextPaint = Paint().apply {
        color = theme.accentArgb
        textSize = 28f
        isFakeBoldText = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("VYNCE MUSIC", 108f, 142f, badgeTextPaint)

    // Lyrics Text lines
    val lyricsPaint = Paint().apply {
        color = theme.textArgb
        textSize = 52f
        isFakeBoldText = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    var y = 300f
    for (line in selectedLines) {
        // Simple word wrap
        val words = line.split(" ")
        var currentLine = ""
        for (w in words) {
            val testLine = if (currentLine.isEmpty()) w else "$currentLine $w"
            if (lyricsPaint.measureText(testLine) > (width - 160f)) {
                canvas.drawText(currentLine, 80f, y, lyricsPaint)
                y += 75f
                currentLine = w
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, 80f, y, lyricsPaint)
            y += 85f
        }
    }

    // Song & Artist at bottom
    val titlePaint = Paint().apply {
        color = theme.textArgb
        textSize = 42f
        isFakeBoldText = true
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = Paint().apply {
        color = theme.accentArgb
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val bottomY = height - 160f
    canvas.drawText(title, 80f, bottomY, titlePaint)
    canvas.drawText(artist, 80f, bottomY + 50f, artistPaint)

    return bitmap
}
