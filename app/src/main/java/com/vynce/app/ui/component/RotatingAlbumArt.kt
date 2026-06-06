package com.vynce.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest

@Composable
fun RotatingAlbumArt(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val processedUrl = artworkUrl
        ?.replace("http://", "https://")
        ?.replace(Regex("\\b(150x150|50x50)\\b"), "500x500")

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(processedUrl)
            .build(),
        contentDescription = "Album art",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .shadow(24.dp, shape)
            .clip(shape),
        loading = {
            AlbumArtPlaceholder()
        },
        error = {
            AlbumArtPlaceholder()
        }
    )
}

@Composable
private fun AlbumArtPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color(0xFF4A4A6A),
            modifier = Modifier.size(64.dp)
        )
    }
}
