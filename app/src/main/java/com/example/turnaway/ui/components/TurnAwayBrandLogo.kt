package com.example.turnaway.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.turnaway.R

@Composable
fun TurnAwayBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    cornerRadius: Dp = 8.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_turnaway_logo),
        contentDescription = "TurnAway Shield & Hourglass Logo",
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
    )
}
