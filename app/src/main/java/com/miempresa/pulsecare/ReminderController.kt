package com.miempresa.pulsecare

import androidx.appcompat.app.AppCompatActivity

class ReminderController(private val activity: AppCompatActivity) {

    private val repository: ReminderRepository = ReminderRepository()

    fun fetchAllReminders(callback: (List<Reminder>) -> Unit) {
        repository.fetchAllReminders(callback)
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

    fun moveReminderToPending(reminder: Reminder) {
        repository.moveReminderToPending(reminder)
    }

    fun deleteExpiredPendingReminder(reminderId: String) {
        repository.deleteExpiredPendingReminder(reminderId)
    }

}