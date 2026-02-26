package com.gymlog.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

fun suggestWeight(
    lastSessionSets: List<ExerciseSet>,
    lastSessionDate: LocalDate,
    today: LocalDate = LocalDate.now()
): Double? {
    val completedSets = lastSessionSets.filter { it.status != SetStatus.PENDING }
    if (completedSets.isEmpty()) return null

    val baseWeight = completedSets.mapNotNull { it.weightKg }.maxOrNull() ?: return null

    val daysSince = ChronoUnit.DAYS.between(lastSessionDate, today)
    val isStale = daysSince > 10

    val hasAnyFailed = completedSets.any { it.status == SetStatus.FAILED }
    val hasAnyPartial = completedSets.any { it.status == SetStatus.PARTIAL }
    val hasAnyHard = completedSets.any { it.status == SetStatus.HARD }

    val multiplier = if (isStale) {
        when {
            hasAnyFailed || hasAnyPartial -> 0.80
            hasAnyHard -> 0.90
            else -> 1.0
        }
    } else {
        when {
            hasAnyFailed -> 0.95
            hasAnyHard || hasAnyPartial -> 1.0
            else -> 1.05
        }
    }

    val rounded = roundToNearest2Point5(baseWeight * multiplier)
    val baseRounded = roundToNearest2Point5(baseWeight)

    return when {
        multiplier > 1.0 && rounded <= baseRounded -> baseRounded + 2.5
        multiplier < 1.0 && rounded >= baseRounded -> (baseRounded - 2.5).coerceAtLeast(0.0)
        else -> rounded
    }
}

fun roundToNearest2Point5(kg: Double): Double {
    return (kg / 2.5).roundToLong() * 2.5
}
