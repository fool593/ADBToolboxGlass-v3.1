package com.example.adbtoolbox.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 重写控件集（v3.1 UI2）：现代"描边玻璃瓦片"风格。
 * - 液态玻璃材质/引擎完全不动（保留应用身份与既有性能优化）；
 * - 控件层全面重设计：更柔的 18dp 圆角、半透明白色描边、浅色填充、
 *   按压缩放弹簧动画（0.96）、统一 14sp 加粗文字与更克制的配色；
 * - 控件只负责自身视觉，不触碰 Backdrop 内部，构建/性能风险独立。
 */
object NewUi2 {

    /** 描边玻璃瓦片按钮：替代老式纯色实心按钮。 */
    @Composable
    fun Tile(
        label: String,
        accent: Color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (pressed) 0.955f else 1f,
            spring(dampingRatio = 0.62f, stiffness = 520f)
        )
        val shape = RoundedCornerShape(18.dp)
        Box(
            modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .background(accent.copy(alpha = 0.13f))
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.32f)), shape)
                .clickable(interactionSource = interaction, indication = null) { onClick() }
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .align(Alignment.TopEnd)
                    .padding(0.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(0.dp))
            BasicText(
                label,
                style = TextStyle(
                    color = accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            )
        }
    }

    /** 分区标题（重写版）：小标签 + 更柔的间距节奏。 */
    @Composable
    fun SectionTitle(
        title: String,
        tint: Color,
        modifier: Modifier = Modifier
    ) {
        Box(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            BasicText(
                title,
                style = TextStyle(
                    color = tint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}