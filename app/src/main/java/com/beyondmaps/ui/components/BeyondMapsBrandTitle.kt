package com.beyondmaps.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beyondmaps.R
import com.beyondmaps.ui.theme.AccentBlue
import com.beyondmaps.ui.theme.TextPrimary

@Composable
fun BeyondMapsBrandTitle(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.displayLarge,
    logoSize: Dp = 28.dp,
    spacing: Dp = 8.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Image(
            painter = painterResource(R.drawable.beyondmaps_logo),
            contentDescription = "Beyond Maps logo",
            modifier = Modifier.size(logoSize),
        )
        Text(
            text = buildAnnotatedString {
                withStyle(style = androidx.compose.ui.text.SpanStyle(color = TextPrimary)) { append("Beyond") }
                withStyle(style = androidx.compose.ui.text.SpanStyle(color = AccentBlue)) { append("Maps") }
            },
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
