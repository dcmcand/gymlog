package com.gymlog.app.ui.workout

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymlog.app.data.SetStatus
import com.gymlog.app.ui.theme.SetEasyColor
import com.gymlog.app.ui.theme.SetFailedColor
import com.gymlog.app.ui.theme.SetHardColor
import com.gymlog.app.ui.theme.SetPartialColor
import com.gymlog.app.ui.theme.SetPendingColor

@Composable
fun SetCircleIndicator(
    setNumber: Int,
    status: SetStatus,
    repsCompleted: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (status) {
        SetStatus.PENDING -> Color.Transparent
        SetStatus.EASY -> SetEasyColor
        SetStatus.HARD -> SetHardColor
        SetStatus.PARTIAL -> SetPartialColor
        SetStatus.FAILED -> SetFailedColor
    }

    val contentColor = when (status) {
        SetStatus.PENDING -> SetPendingColor
        else -> Color.White
    }

    val displayText = when (status) {
        SetStatus.PENDING -> setNumber.toString()
        SetStatus.FAILED -> "X"
        else -> (repsCompleted ?: setNumber).toString()
    }

    val borderModifier = if (status == SetStatus.PENDING) {
        Modifier.border(2.dp, SetPendingColor, CircleShape)
    } else {
        Modifier
    }

    Surface(
        color = backgroundColor,
        shape = CircleShape,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(borderModifier)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = displayText,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
