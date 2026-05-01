package com.beyondmaps.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beyondmaps.ui.theme.AccentBlue
import com.beyondmaps.ui.theme.BgAiBubble
import com.beyondmaps.ui.theme.BgUserBubble
import com.beyondmaps.ui.theme.BorderCard
import com.beyondmaps.ui.theme.BorderUser
import com.beyondmaps.ui.theme.HighlightBlue
import com.beyondmaps.ui.theme.TextSecondary

@Composable
fun AiBubble(text: String, sources: List<String>?, isOcr: Boolean = false) {
    val isError = text.contains("could not start the offline model", ignoreCase = true) ||
        text.contains("offline model is not ready", ignoreCase = true) ||
        text.contains("failed while generating", ignoreCase = true)

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(Color(0x4D1E468C), RoundedCornerShape(7.dp))
                .border(0.5.dp, Color(0x333C78DC), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .border(1.dp, AccentBlue, RoundedCornerShape(6.dp)),
            )
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .background(AccentBlue, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(modifier = Modifier.size(9.dp))
        Column {
            Box(
                modifier = Modifier
                    .background(
                        if (isError) Color(0x151C3457) else BgAiBubble,
                        RoundedCornerShape(topStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp),
                    )
                    .border(
                        if (isError) 0.7.dp else 0.5.dp,
                        if (isError) Color(0x34568FDC) else BorderCard,
                        RoundedCornerShape(topStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp),
                    )
                    .padding(vertical = 11.dp, horizontal = 14.dp)
            ) {
                Column {
                    Text(
                        text = parseHighlighted(text),
                        style = if (isOcr) {
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp,
                            )
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (isError) Color(0xFFA5C2E8) else TextSecondary,
                    )
                    if (isError) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "If this keeps happening, verify local model files and retry.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6E88AB),
                        )
                    }
                }
            }
            if (!sources.isNullOrEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    sources.forEach {
                        Box(
                            modifier = Modifier
                                .border(0.5.dp, Color(0x0FFFFFFF), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = it,
                                color = Color(0xFF6F88AD),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserBubble(text: String, hadImageAttachment: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hadImageAttachment) {
            Text(
                text = "📷",
                modifier = Modifier.padding(end = 6.dp),
                fontSize = 14.sp,
            )
        }
        Box(
            modifier = Modifier
                .background(
                    BgUserBubble,
                    RoundedCornerShape(topStart = 13.dp, topEnd = 4.dp, bottomEnd = 13.dp, bottomStart = 13.dp)
                )
                .border(
                    0.5.dp,
                    BorderUser,
                    RoundedCornerShape(topStart = 13.dp, topEnd = 4.dp, bottomEnd = 13.dp, bottomStart = 13.dp)
                )
                .padding(vertical = 10.dp, horizontal = 13.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF8AAACE)),
            )
        }
    }
}

private fun parseHighlighted(text: String): AnnotatedString {
    val pattern = Regex("\\*\\*(.*?)\\*\\*")
    return buildAnnotatedString {
        var last = 0
        pattern.findAll(text).forEach { match ->
            append(text.substring(last, match.range.first))
            pushStyle(SpanStyle(color = HighlightBlue, fontWeight = FontWeight.Medium))
            append(match.groupValues[1])
            pop()
            last = match.range.last + 1
        }
        if (last < text.length) append(text.substring(last))
    }
}
