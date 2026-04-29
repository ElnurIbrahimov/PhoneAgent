package com.phoneagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PhoneAgentShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

val BubbleShapeUser = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
val BubbleShapeAgent = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(12.dp)
