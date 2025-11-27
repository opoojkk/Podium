package com.opoojkk.podium.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
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
 * @param displaySize Display size in Dp - image will be decoded to 2x this size for crisp display on high DPI screens
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
    val context = LocalPlatformContext.current
    val density = LocalDensity.current

    // Convert Dp to pixels using actual density
    // Use 2x size for high DPI displays to avoid blurry images
    val pixelSize = with(density) {
        (displaySize * 2f).roundToPx()
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(model ?: "")
            .size(Size(pixelSize, pixelSize))
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = loading?.let { { it() } },
        error = error?.let { { it() } }
    )
}
