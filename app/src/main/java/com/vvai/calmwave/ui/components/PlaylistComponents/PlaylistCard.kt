package com.vvai.calmwave.ui.components.PlaylistComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun PlaylistCard(
    title: String,
    subtitle: String,
    color: Color,
    isFavorite: Boolean = false,
    onFavoriteToggle: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        val cardGradient = Brush.horizontalGradient(
            listOf(color, lerp(color, Color.Black, 0.18f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = cardGradient, shape = RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                val thumbGradient = Brush.horizontalGradient(listOf(color, lerp(color, Color.Black, 0.12f)))
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(brush = thumbGradient)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.95f))
                }

                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Desfavoritar" else "Favoritar",
                        tint = if (isFavorite) Color(0xFFFFC0C0) else Color.White.copy(alpha = 0.9f)
                    )
                }
                IconButton(onClick = { /* opções */ }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Mais", tint = Color.White.copy(alpha = 0.9f))
                }
            }
        }
    }
}