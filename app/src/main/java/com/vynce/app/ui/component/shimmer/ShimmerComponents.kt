package com.vynce.app.ui.component.shimmer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vynce.app.ui.utils.shimmer

@Composable
fun HomeItemShimmer(itemCount: Int = 4) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Column {
        Box(
            Modifier
                .width(150.dp)
                .height(36.dp)
                .padding(vertical = 8.dp)
                .background(
                    color = placeholderColor,
                ).clip(RoundedCornerShape(10))
                .shimmer(),
        )
        LazyRow(userScrollEnabled = false) {
            items(itemCount) {
                PlaylistShimmer()
            }
        }
    }
}

@Composable
fun PlaylistShimmer() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Column(
        Modifier
            .height(220.dp)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(
                    RoundedCornerShape(10),
                ).background(
                    color = placeholderColor,
                ).shimmer(),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Box(
            Modifier
                .width(116.dp)
                .height(18.dp)
                .clip(
                    RoundedCornerShape(10),
                ).background(
                    color = placeholderColor,
                ).shimmer(),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Box(
            Modifier
                .width(88.dp)
                .height(14.dp)
                .clip(
                    RoundedCornerShape(10),
                ).background(
                    color = placeholderColor,
                ).shimmer(),
        )
    }
}

@Composable
fun QuickPicksShimmerItem() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Row(
        Modifier
            .height(64.dp)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10))
                .background(placeholderColor)
                .shimmer(),
        )
        Column(
            Modifier
                .padding(start = 10.dp)
                .wrapContentHeight(align = Alignment.CenterVertically)
                .align(Alignment.CenterVertically),
        ) {
            Box(
                Modifier
                    .width(240.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(10))
                    .background(placeholderColor)
                    .shimmer(),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                Modifier
                    .width(180.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(10))
                    .background(placeholderColor)
                    .shimmer(),
            )
        }
    }
}

@Composable
fun QuickPicksShimmer() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Column {
        Box(
            Modifier
                .width(150.dp)
                .height(36.dp)
                .padding(vertical = 8.dp)
                .background(
                    color = placeholderColor,
                ).clip(RoundedCornerShape(10))
                .shimmer(),
        )
        Column(modifier = Modifier.height(212.dp)) {
            repeat(3) {
                QuickPicksShimmerItem()
            }
        }
    }
}

@Composable
fun HomeShimmer() {
    Column(
        Modifier.padding(horizontal = 15.dp),
    ) {
        QuickPicksShimmer()
        repeat(3) {
            HomeItemShimmer()
        }
    }
}
