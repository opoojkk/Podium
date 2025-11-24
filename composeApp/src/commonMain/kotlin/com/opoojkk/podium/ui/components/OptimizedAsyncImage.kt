package com.opoojkk.podium.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size

/**
 * Optimized image loading component that limits decoded image size to display size
 *
 * Performance benefits:
 * - Reduces memory usage by not decoding full-size images
 * - Faster decode time for smaller display sizes
 * - Better scroll performance in lists
 *
 * @param model Image URL or resource
 * @param contentDescription Accessibility description
 * @param displaySize Display size in Dp - image will be decoded to this size
 * @param modifier Compose modifier
 * @param contentScale How to scale the image in its container
 * @param loading Composable to show while loading
 * @param error Composable to show on error
 */
@Composable
fun OptimizedAsyncImage(
    model: Any?,
    contentDescription: String?,
    displaySize: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    loading: @Composable (() -> Unit)? = null,
    error: @Composable (() -> Unit)? = null,
) {
    // Convert Dp to pixels (approximate, density will be handled by Coil)
    // Using 2.5 as a rough multiplier for common screen densities (between mdpi and xxxhdpi)
    val pixelSize = (displaySize.value * 2.5f).toInt()

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(model ?: "")
            .size(Size(pixelSize, pixelSize))
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = loading?.let { { it() } },
        error = error?.let { { it() } }
    )
}
