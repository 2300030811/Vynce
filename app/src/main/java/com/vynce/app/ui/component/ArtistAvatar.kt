package com.vynce.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.vynce.app.ui.theme.VyncePurple

private val AvatarGradientPalettes = listOf(
    listOf(Color(0xFF6B21A8), Color(0xFF1E1B4B), Color(0xFF0F172A)),
    listOf(Color(0xFF1E40AF), Color(0xFF172554), Color(0xFF0F172A)),
    listOf(Color(0xFF831843), Color(0xFF4C0519), Color(0xFF0F172A)),
    listOf(Color(0xFF065F46), Color(0xFF064E3B), Color(0xFF0F172A)),
    listOf(Color(0xFF9A3412), Color(0xFF431407), Color(0xFF0F172A)),
    listOf(Color(0xFF3730A3), Color(0xFF1E1B4B), Color(0xFF0F172A)),
    listOf(Color(0xFF155E75), Color(0xFF083344), Color(0xFF0F172A)),
)

/**
 * High-fidelity Artist Avatar with graceful Monogram Initial fallback matching Vynce Web.
 * When imageUrl is missing or fails, renders a tailored ambient gradient circle with the artist's first initial.
 */
@Composable
fun ArtistAvatar(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val initial = (name.trim().firstOrNull()?.uppercaseChar() ?: 'A').toString()
    val paletteIndex = kotlin.math.abs(name.hashCode()) % AvatarGradientPalettes.size
    val gradientColors = AvatarGradientPalettes[paletteIndex]

    val validUrl = imageUrl?.trim()?.takeIf {
        it.isNotEmpty() &&
        !it.equals("false", ignoreCase = true) &&
        !it.equals("null", ignoreCase = true) &&
        !it.contains("artist-default", ignoreCase = true) &&
        !it.contains("default-artist", ignoreCase = true) &&
        !it.contains("default_artist", ignoreCase = true) &&
        !it.contains("artist-placeholder", ignoreCase = true)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        // Monogram fallback always rendered underneath
        Text(
            text = initial,
            color = VyncePurple,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )

        if (validUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(validUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
