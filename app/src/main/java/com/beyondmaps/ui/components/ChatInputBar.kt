package com.beyondmaps.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    isThinking: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    placeholder: String,
) {
    val InputBorderIdle = Color(0x12FFFFFF)
    val InputBorderActive = Color(0x38508CFF)
    val SendGradientStart = Color(0xB2285AC8)
    val SendGradientEnd = Color(0x8C14378C)
    val SendIconColor = Color(0xD985AFFF)
    val StopIconColor = Color(0xB2648FFF)
    val ChipText = Color(0xFF5C7CAD)
    val ChipBorder = Color(0x3379B2FF)
    val ChipBackground = Color(0x14253F67)
    val ChipPressedBackground = Color(0x244981CF)
    val ChipPressedText = Color(0xFF9DCDFF)
    val ChipPressedBorder = Color(0x4D7BB7FF)
    val ChipAccentDot = Color(0xFF78B5FF)

    val suggestions = listOf(
        "Where to eat late?",
        "Transit tips",
        "What to avoid",
        "Tipping customs",
        "Pocket phrases",
    )
    val soraFamily = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0x0AFFFFFF))
            .padding(top = 8.dp, start = 14.dp, end = 14.dp, bottom = 24.dp),
    ) {
        InputShell(
            value = value,
            onValueChange = onValueChange,
            isThinking = isThinking,
            onSend = onSend,
            onStop = onStop,
            placeholder = placeholder,
            soraFamily = soraFamily,
            inputBorderIdle = InputBorderIdle,
            inputBorderActive = InputBorderActive,
            sendGradientStart = SendGradientStart,
            sendGradientEnd = SendGradientEnd,
            sendIconColor = SendIconColor,
            stopIconColor = StopIconColor,
        )
        AnimatedVisibility(
            visible = value.isEmpty() && !isThinking,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(160)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Spacer(modifier = Modifier.size(4.dp))
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        text = suggestion,
                        onClick = { onValueChange(suggestion) },
                        chipText = ChipText,
                        chipBorder = ChipBorder,
                        chipBackground = ChipBackground,
                        chipPressedBackground = ChipPressedBackground,
                        chipPressedText = ChipPressedText,
                        chipPressedBorder = ChipPressedBorder,
                        chipAccentDot = ChipAccentDot,
                    )
                }
            }
        }
    }
}

@Composable
private fun InputShell(
    value: String,
    onValueChange: (String) -> Unit,
    isThinking: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    placeholder: String,
    soraFamily: FontFamily,
    inputBorderIdle: Color,
    inputBorderActive: Color,
    sendGradientStart: Color,
    sendGradientEnd: Color,
    sendIconColor: Color,
    stopIconColor: Color,
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused || isThinking) inputBorderActive else inputBorderIdle,
        animationSpec = tween(300),
        label = "inputBorder",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused || isThinking) 1f else 0f,
        animationSpec = tween(350),
        label = "inputGlowAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            listOf(Color(0x06285ACC), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                            radius = size.width * 0.6f,
                        ),
                        alpha = glowAlpha,
                    )
                }
            }
            .background(Color(0x08FFFFFF), RoundedCornerShape(28.dp))
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
            .padding(start = 20.dp, top = 5.dp, end = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = soraFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF8098BE),
                lineHeight = 20.sp,
            ),
            cursorBrush = SolidColor(Color(0xFF5A9AEE)),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontFamily = soraFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFF2A3A55),
                            lineHeight = 20.sp,
                        ),
                    )
                }
                inner()
            }
        )

        SendButton(
            isThinking = isThinking,
            onClick = { if (isThinking) onStop() else onSend() },
            sendGradientStart = sendGradientStart,
            sendGradientEnd = sendGradientEnd,
            sendIconColor = sendIconColor,
            stopIconColor = stopIconColor,
        )
    }
}

@Composable
private fun SendButton(
    isThinking: Boolean,
    onClick: () -> Unit,
    sendGradientStart: Color,
    sendGradientEnd: Color,
    sendIconColor: Color,
    stopIconColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.89f else 1f,
        animationSpec = spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        ),
        label = "sendPressScale",
    )
    val transition = updateTransition(targetState = isThinking, label = "sendState")
    val iconAlpha by transition.animateFloat(transitionSpec = { tween(200) }, label = "iconAlpha") { if (it) 1f else 0f }
    val iconScale by transition.animateFloat(
        transitionSpec = { spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium) },
        label = "iconScale",
    ) { if (it) 1f else 0.7f }

    val ringTransition = rememberInfiniteTransition(label = "ring")
    val ringAlpha by ringTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringAlpha",
    )
    val ringRadius by ringTransition.animateFloat(
        initialValue = 21f,
        targetValue = 29f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ringRadius",
    )

    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isThinking) {
                    drawCircle(
                        color = Color(0xFF3C6EDC).copy(alpha = ringAlpha),
                        radius = ringRadius.dp.toPx(),
                    )
                }
            }
            .background(
                brush = if (isThinking) Brush.linearGradient(listOf(Color(0x260F1E50), Color(0x260F1E50))) else Brush.linearGradient(
                    listOf(sendGradientStart, sendGradientEnd)
                ),
                shape = CircleShape,
            )
            .border(
                width = 0.5.dp,
                color = if (isThinking) Color(0x2E3264C8) else Color(0x40508FFF),
                shape = CircleShape,
            )
            .drawBehind {
                if (!isThinking) {
                    drawArc(
                        color = Color(0x12FFFFFF),
                        startAngle = 200f,
                        sweepAngle = 140f,
                        useCenter = false,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ArrowIcon(
            modifier = Modifier
                .size(15.dp)
                .graphicsLayer {
                    alpha = 1f - iconAlpha
                    scaleX = if (isThinking) 0.7f else 1f
                    scaleY = if (isThinking) 0.7f else 1f
                },
            color = sendIconColor,
        )
        StopGridIcon(
            modifier = Modifier
                .size(11.dp)
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleX = iconScale
                    scaleY = iconScale
                },
            color = stopIconColor,
        )
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    chipText: Color,
    chipBorder: Color,
    chipBackground: Color,
    chipPressedBackground: Color,
    chipPressedText: Color,
    chipPressedBorder: Color,
    chipAccentDot: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        ),
        label = "chipScale",
    )
    val bg by animateColorAsState(
        targetValue = if (isPressed) chipPressedBackground else chipBackground,
        animationSpec = tween(180),
        label = "chipBg",
    )
    val border by animateColorAsState(
        targetValue = if (isPressed) chipPressedBorder else chipBorder,
        animationSpec = tween(180),
        label = "chipBorder",
    )
    val textColor by animateColorAsState(
        targetValue = if (isPressed) chipPressedText else chipText,
        animationSpec = tween(180),
        label = "chipText",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isPressed) 0.96f else 1f
            }
            .background(bg, RoundedCornerShape(20.dp))
            .border(0.75.dp, border, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 7.dp, horizontal = 13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(chipAccentDot, CircleShape),
            )
            Text(
                text = text,
                fontSize = 10.8.sp,
                fontWeight = FontWeight.Normal,
                color = textColor,
                letterSpacing = 0.1.sp,
            )
        }
    }
}

@Composable
private fun ArrowIcon(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.8f)
            lineTo(size.width * 0.78f, size.height * 0.22f)
            moveTo(size.width * 0.78f, size.height * 0.22f)
            lineTo(size.width * 0.45f, size.height * 0.22f)
            moveTo(size.width * 0.78f, size.height * 0.22f)
            lineTo(size.width * 0.78f, size.height * 0.55f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.55.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun StopGridIcon(modifier: Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val unit = size.width / 12f
        fun drawCell(x: Float, y: Float) {
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x * unit, y * unit),
                size = androidx.compose.ui.geometry.Size(4f * unit, 4f * unit),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * unit, 1f * unit),
            )
        }
        drawCell(1.5f, 1.5f)
        drawCell(6.5f, 1.5f)
        drawCell(1.5f, 6.5f)
        drawCell(6.5f, 6.5f)
    }
}
