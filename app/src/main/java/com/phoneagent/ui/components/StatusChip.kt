package com.phoneagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.phoneagent.ui.theme.*

@Composable
fun StatusChip(
    text: String,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) Success.copy(alpha = 0.15f) else Error.copy(alpha = 0.15f)
    val textColor = if (isActive) Success else Error
    val dotColor = if (isActive) Success else Error

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, RoundedCornerShape(50))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
