package com.opoojkk.podium.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.opoojkk.podium.data.model.Chapter

/**
 * 带章节标记的进度条组件
 * 在进度条上显示章节标记点，点击可以跳转到对应章节
 */
@Composable
fun ChapterProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    chapterMarkerColor: Color = MaterialTheme.colorScheme.tertiary,
) {
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // 计算点击位置对应的时间
                        val clickedProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        val seekPosition = (clickedProgress * durationMs).toLong()
                        onSeekTo(seekPosition)
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val trackHeight = 4.dp.toPx()
            val markerRadius = 6.dp.toPx()
            val progressBarY = canvasHeight / 2

            // 绘制轨道背景
            drawLine(
                color = trackColor,
                start = Offset(0f, progressBarY),
                end = Offset(canvasWidth, progressBarY),
                strokeWidth = trackHeight
            )

            // 绘制已播放进度
            val progressWidth = canvasWidth * progress
            drawLine(
                color = progressColor,
                start = Offset(0f, progressBarY),
                end = Offset(progressWidth, progressBarY),
                strokeWidth = trackHeight
            )

            // 绘制章节标记点
            if (durationMs > 0) {
                chapters.forEach { chapter ->
                    val chapterProgress = chapter.startTimeMs.toFloat() / durationMs.toFloat()
                    val markerX = canvasWidth * chapterProgress

                    // 只绘制在有效范围内的标记
                    if (chapterProgress in 0f..1f) {
                        // 绘制章节标记圆点
                        drawCircle(
                            color = chapterMarkerColor,
                            radius = markerRadius,
                            center = Offset(markerX, progressBarY)
                        )
                    }
                }
            }

            // 绘制当前播放位置指示器
            drawCircle(
                color = progressColor,
                radius = markerRadius * 1.5f,
                center = Offset(progressWidth, progressBarY)
            )
        }
    }
}
