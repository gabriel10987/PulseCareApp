package com.miempresa.pulsecare

import com.google.firebase.database.*

class ReminderRepository {

    private val databaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("reminders")

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

    fun fetchReminderById(id: String, callback: (Reminder?) -> Unit) {
        databaseReference.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reminder = snapshot.getValue(Reminder::class.java)
                reminder?.id = snapshot.key
                callback(reminder)
            }

            override fun onCancelled(error: DatabaseError) {
                callback(null)
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

    fun updateReminderState(reminderId: String, state: String, callback: (Boolean) -> Unit) {
        val reminderStateRef = FirebaseDatabase.getInstance().reference.child("closestReminders").child("state")
        reminderStateRef.setValue(state).addOnCompleteListener { task ->
            callback(task.isSuccessful)
        }
    }

    fun decreasePills(reminderId: String, callback: (Boolean) -> Unit) {
        val reminderRef = databaseReference.child(reminderId)
        reminderRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val reminder = mutableData.getValue(Reminder::class.java) ?: return Transaction.success(mutableData)
                reminder.pills -= 1
                mutableData.value = reminder
                return Transaction.success(mutableData)
            }

            override fun onComplete(databaseError: DatabaseError?, committed: Boolean, dataSnapshot: DataSnapshot?) {
                callback(committed)
            }
        })
    }

}