package com.opoojkk.podium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Displays podcast artwork with an intelligent placeholder fallback.
 *
 * If the artwork URL is valid, displays the image using OptimizedAsyncImage.
 * Otherwise, shows initials derived from the title on a colored background.
 *
 * @param artworkUrl URL of the artwork image, can be null or empty
 * @param title Title used to generate initials for the placeholder
 * @param size Size of the artwork box
 * @param modifier Additional modifier for the component
 * @param cornerRadius Corner radius for the artwork box
 * @param contentDescription Accessibility description for the image
 */
@Composable
fun ArtworkWithPlaceholder(
    artworkUrl: String?,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentDescription: String? = title,
) {
    // Memoize initials calculation to avoid recomputation on every recomposition
    val initials = remember(title) {
        derivedStateOf {
            title.trim()
                .split(" ", limit = 2)
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .take(2)
                .joinToString("")
                .takeIf { it.isNotBlank() }
                ?: "播客"
        }
    }.value

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            OptimizedAsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                displaySize = size,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop,
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                },
                error = {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
