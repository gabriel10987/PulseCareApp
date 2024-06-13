package com.miempresa.pulsecare

import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class ReminderController(private val activity: AppCompatActivity) {

    private val repository: ReminderRepository = ReminderRepository()

    fun fetchAllReminders(callback: (List<Reminder>) -> Unit) {
        repository.fetchAllReminders(callback)
    }

    fun fetchReminderById(id: String, callback: (Reminder?) -> Unit) {
        repository.fetchReminderById(id, callback)
    }

    fun addReminder(reminder: Reminder, callback: (Boolean) -> Unit) {
        repository.addReminder(reminder, callback)
    }

    fun updateReminder(reminder: Reminder, callback: (Boolean) -> Unit) {
        repository.updateReminder(reminder, callback)
    }

    fun deleteReminder(reminder: Reminder, callback: (Boolean) -> Unit) {
        repository.deleteReminder(reminder, callback)
    }

    fun updateReminderTime(reminder: Reminder, newTime: Long, callback: (Boolean) -> Unit) {
        repository.updateReminderTime(reminder, newTime, callback)
    }

}