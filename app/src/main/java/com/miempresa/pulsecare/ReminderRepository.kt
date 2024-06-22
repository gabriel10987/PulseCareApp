package com.miempresa.pulsecare

import com.google.firebase.database.*
import java.util.Calendar

class ReminderRepository {

    private val databaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("reminders")
    private val pendingRemindersReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("pendingReminders")
    private val emergencyStateReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("emergencyState")

    fun fetchAllReminders(callback: (List<Reminder>) -> Unit) {
        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val remindersList = mutableListOf<Reminder>()
                for (snapshot in dataSnapshot.children) {
                    val reminder = snapshot.getValue(Reminder::class.java)
                    reminder?.id = snapshot.key // Asignar el ID de Firebase
                    reminder?.let { remindersList.add(it) }
                }
                remindersList.sortBy { it.reminderTime }
                callback(remindersList)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Manejar errores de Firebase
            }
        })
    }

    fun addReminder(reminder: Reminder, callback: (Boolean) -> Unit) {
        val newReminderRef = databaseReference.push()
        reminder.id = newReminderRef.key // Asignar el ID de Firebase al recordatorio
        newReminderRef.setValue(reminder).addOnCompleteListener { task ->
            callback(task.isSuccessful)
        }
    }

    fun updateReminder(reminder: Reminder, callback: (Boolean) -> Unit) {
        reminder.id?.let {
            val reminderRef = databaseReference.child(it)
            reminderRef.setValue(reminder).addOnCompleteListener { task ->
                callback(task.isSuccessful)
            }
        }
    }

    fun deleteReminder(reminder: Reminder, callback: (Boolean) -> Unit) {
        reminder.id?.let {
            val reminderRef = databaseReference.child(it)
            reminderRef.removeValue().addOnCompleteListener { task ->
                callback(task.isSuccessful)
            }
        }
    }

    fun updateReminderTime(reminder: Reminder, newTime: Long, callback: (Boolean) -> Unit) {
        reminder.id?.let {
            val reminderRef = databaseReference.child(it).child("reminderTime")
            reminderRef.setValue(newTime).addOnCompleteListener { task ->
                callback(task.isSuccessful)
            }
        }
    }

    fun moveReminderToPending(reminder: Reminder) {
        reminder.id?.let {
            val pendingReminderRef = pendingRemindersReference.child(it)
            pendingReminderRef.setValue(reminder)
            // Eliminar el recordatorio de la lista de recordatorios
            databaseReference.child(it).removeValue()
        }
    }

    fun deleteExpiredPendingReminder (reminderId: String) {
        pendingRemindersReference.child(reminderId).removeValue()
    }

}