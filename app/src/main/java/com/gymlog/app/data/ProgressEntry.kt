package com.gymlog.app.data

import java.time.LocalDate

data class ProgressEntry(
    val maxWeight: Double,
    val date: LocalDate
)

data class CardioProgressEntry(
    val maxDistance: Int,
    val minDuration: Int?,
    val date: LocalDate
)

data class TimeProgressEntry(
    val minDuration: Int,
    val date: LocalDate
)

data class DistanceProgressEntry(
    val maxDistance: Int,
    val date: LocalDate
)
