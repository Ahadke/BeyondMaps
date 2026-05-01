package com.beyondmaps.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondmaps.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    isThinking: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    placeholder: String,
    pendingImageUri: Uri? = null,
    onClearImage: () -> Unit = {},
    onChooseTranslateText: () -> Unit = {},
    onChooseIdentify: () -> Unit = {},
) {
    val InputBorderIdle = Color(0x12FFFFFF)
    val InputBorderActive = Color(0x38508CFF)
    val SendGradientStart = Color(0xB2285AC8)
    val SendGradientEnd = Color(0x8C14378C)
    val SendIconColor = Color(0xD985AFFF)
    val StopIconColor = Color(0xB2648FFF)
    val soraFamily = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default

    var attachExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, Color(0x0AFFFFFF))
            .padding(top = 8.dp, start = 14.dp, end = 14.dp, bottom = 24.dp),
    ) {
        if (pendingImageUri != null) {
            PendingImagePreview(
                uri = pendingImageUri,
                onRemove = onClearImage,
                enabled = !isThinking,
            )
        }
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
            attachExpanded = attachExpanded,
            onToggleAttach = { attachExpanded = !attachExpanded },
        )
        AnimatedVisibility(
            visible = attachExpanded && !isThinking,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeChip(label = "Translate text", onClick = {
                    attachExpanded = false
                    onChooseTranslateText()
                })
                ModeChip(label = "Identify", onClick = {
                    attachExpanded = false
                    onChooseIdentify()
                })
            }
        }
    }
}

@Composable
private fun PendingImagePreview(
    uri: Uri,
    onRemove: () -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = "Image attached",
                fontSize = 11.sp,
                color = Color(0xFF6E88AB),
            )
        }
        TextButton(onClick = onRemove, enabled = enabled) {
            Text("Remove", fontSize = 12.sp, color = Color(0xFF7AB0E8))
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
    attachExpanded: Boolean,
    onToggleAttach: () -> Unit,
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
        AttachPlusButton(
            expanded = attachExpanded,
            enabled = !isThinking,
            onClick = onToggleAttach,
        )

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
private fun ModeChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                Brush.linearGradient(listOf(Color(0x2430547E), Color(0x1A22395C))),
                RoundedCornerShape(18.dp)
            )
            .border(0.9.dp, Color(0x4A7AB8FF), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val icon = if (label == "Translate text") R.drawable.ic_phrases else R.drawable.ic_guide
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color(0xFF98C7FF),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFB2D7FF),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun AttachPlusButton(
    expanded: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(200),
        label = "plusRotation",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                brush = Brush.linearGradient(listOf(Color(0xAA234A9A), Color(0x8A142C63))),
                shape = CircleShape,
            )
            .border(0.7.dp, Color(0x5088BEFF), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = Color(0xFFD7E8FF),
            fontSize = 18.sp,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
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
