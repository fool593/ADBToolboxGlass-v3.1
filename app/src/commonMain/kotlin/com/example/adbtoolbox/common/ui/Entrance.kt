package com.example.adbtoolbox.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 玻璃子页布局统一模板：分区/卡片错峰入场（淡入 + 上浮 10dp，index×60ms 级联）。
 * 15 个子页统一用它包卡片容器，即可获得与首页一致的入场节奏。
 */
@Composable
fun StaggeredCard(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * 60L))
        appear = true
    }
    val shown by animateFloatAsState(
        if (appear) 1f else 0f,
        tween(420, easing = FastOutSlowInEasing)
    )
    Box(
        modifier.graphicsLayer {
            alpha = shown
            translationY = (1f - shown) * 10f.dp.toPx()
        }
    ) {
        content()
    }
}