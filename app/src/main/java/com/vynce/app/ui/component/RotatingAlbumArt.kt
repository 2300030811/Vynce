package com.vynce.app.ui.component

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun RotatingAlbumArt(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(artworkUrl?.replace("http://", "https://"))
            .build(),
        contentDescription = "Album art",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(1f)
            .shadow(24.dp, shape)
            .clip(shape)
    )
}

