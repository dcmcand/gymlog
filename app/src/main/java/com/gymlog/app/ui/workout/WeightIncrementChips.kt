package com.gymlog.app.ui.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeightIncrementChips(
    onIncrement: (Double) -> Unit,
    onDecrement: (Double) -> Unit
) {
    val increments = listOf(1.25, 2.5, 5.0, 10.0, 20.0)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        increments.forEach { increment ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.combinedClickable(
                    onClick = { onIncrement(increment) },
                    onLongClick = { onDecrement(increment) }
                )
            ) {
                Text(
                    text = if (increment == increment.toLong().toDouble())
                        "+${increment.toLong()}"
                    else
                        "+$increment",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
