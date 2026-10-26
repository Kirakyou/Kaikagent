package com.visualagent.hyperos.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visualagent.hyperos.R
import com.visualagent.hyperos.ui.sound.McSoundManager

object McColors {
    val BgDarkStone = Color(0xFF1E1E1F)
    val PanelGray = Color(0xFFC6C6C6)
    val PanelDarkGray = Color(0xFF313233)
    val BorderOuterBlack = Color(0xFF0A0A0A)

    val GreenBase = Color(0xFF3C8527)
    val GreenLight = Color(0xFF52A535)
    val GreenDark = Color(0xFF2A641C)

    val GrayBase = Color(0xFF7D7D7D)
    val GrayLight = Color(0xFFAAAAAA)
    val GrayDark = Color(0xFF4A4A4A)

    val RedBase = Color(0xFFB3312C)
    val RedLight = Color(0xFFD84640)
    val RedDark = Color(0xFF7A1E1B)
}

enum class McButtonVariant { GREEN, GRAY, RED }

/**
 * 将 res/drawable-nodpi 中的 Minecraft 像素贴图（泥土、圆石、岩石等）作为无缝平铺背景渲染
 */
@Composable
fun Modifier.mcTiledTextureBackground(
    @DrawableRes resId: Int,
    darkenAlpha: Float = 0.25f
): Modifier {
    val bitmap = ImageBitmap.imageResource(id = resId)
    val brush = remember(bitmap) {
        ShaderBrush(
            ImageShader(
                image = bitmap,
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated
            )
        )
    }
    return this.drawBehind {
        drawRect(brush = brush)
        if (darkenAlpha > 0f) {
            drawRect(color = Color.Black.copy(alpha = darkenAlpha))
        }
    }
}

/**
 * Minecraft 经典立体斜角边框修饰符
 */
fun Modifier.mcBevelPanel(
    baseColor: Color = McColors.PanelDarkGray,
    highlightColor: Color = Color(0xFF6A6B6C),
    shadowColor: Color = Color(0xFF161617),
    borderPx: Dp = 3.dp
): Modifier = this.drawBehind {
    val b = borderPx.toPx()
    val w = size.width
    val h = size.height

    drawRect(color = McColors.BorderOuterBlack, size = size)
    drawRect(
        color = baseColor,
        topLeft = Offset(b, b),
        size = Size(w - b * 2, h - b * 2)
    )
    drawRect(
        color = highlightColor,
        topLeft = Offset(b, b),
        size = Size(w - b * 2, b)
    )
    drawRect(
        color = highlightColor,
        topLeft = Offset(b, b),
        size = Size(b, h - b * 2)
    )
    drawRect(
        color = shadowColor,
        topLeft = Offset(b, h - b * 2),
        size = Size(w - b * 2, b)
    )
    drawRect(
        color = shadowColor,
        topLeft = Offset(w - b * 2, b),
        size = Size(b, h - b * 2)
    )
}

/**
 * 使用真实 Minecraft 材质贴图 (mc_btn_stone / mc_btn_emerald / mc_btn_redstone) 的像素按钮
 */
@Composable
fun McPixelButton(
    text: String,
    variant: McButtonVariant = McButtonVariant.GRAY,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val texRes = when (variant) {
        McButtonVariant.GREEN -> R.drawable.mc_btn_emerald
        McButtonVariant.RED -> R.drawable.mc_btn_redstone
        McButtonVariant.GRAY -> R.drawable.mc_btn_stone
    }
    val bitmap = ImageBitmap.imageResource(id = texRes)

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                McSoundManager.playClick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.None,
            modifier = Modifier.matchParentSize()
        )
        if (isPressed || !enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (!enabled) 0.55f else 0.28f))
            )
        }
        Text(
            text = text,
            modifier = Modifier
                .padding(contentPadding)
                .padding(top = if (isPressed) 2.dp else 0.dp),
            style = TextStyle(
                color = if (enabled) Color(0xFFF8F8F8) else Color(0xFF9E9E9E),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp,
                shadow = Shadow(
                    color = Color(0xFF181818),
                    offset = Offset(2f, 2f),
                    blurRadius = 0f
                )
            )
        )
    }
}

/**
 * 使用真实 Minecraft 圆石/岩石材质平铺的立体容器面板
 */
@Composable
fun McPanelContainer(
    modifier: Modifier = Modifier,
    useCobblestone: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val texId = if (useCobblestone) R.drawable.mc_cobblestone else R.drawable.mc_stone_panel
    Box(
        modifier = modifier
            .mcTiledTextureBackground(resId = texId, darkenAlpha = if (useCobblestone) 0.45f else 0.18f)
            .mcBevelPanel(
                baseColor = Color.Transparent,
                highlightColor = Color(0x66FFFFFF),
                shadowColor = Color(0x99000000),
                borderPx = 3.dp
            )
            .padding(12.dp),
        content = content
    )
}
