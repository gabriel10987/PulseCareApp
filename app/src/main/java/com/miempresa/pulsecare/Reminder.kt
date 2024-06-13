package com.miempresa.pulsecare

data class Reminder(
    val medicineName: String = "",
    val reminderTime: Long = 0,
    val repeatDays: List<Int> = emptyList(), // Lista de días de repetición (0 domingo, 1 lunes ...)
    val description: String = "",
    var pills: Int = 0,
    var id: String? = null
)
