package com.miempresa.pulsecare

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var reminderController: ReminderController
    private lateinit var remindersRecyclerView: RecyclerView
    private lateinit var remindersAdapter: RemindersAdapter
    private lateinit var closestReminderTextView: TextView
    private lateinit var addReminderButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar vistas
        remindersRecyclerView = findViewById(R.id.remindersRecyclerView)
        closestReminderTextView = findViewById(R.id.closestReminderTextView)
        addReminderButton = findViewById(R.id.addReminderButton)

        // Configurar RecyclerView
        remindersRecyclerView.layoutManager = LinearLayoutManager(this)
        remindersAdapter = RemindersAdapter(this) { reminder -> deleteReminder(reminder) }
        remindersRecyclerView.adapter = remindersAdapter

        // Inicializar controlador
        reminderController = ReminderController(this)
        reminderController.fetchAllReminders  { remindersList -> remindersAdapter.setData(remindersList)
            updateClosestReminder(remindersList)
        }

        // Configurar botón para agregar recordatorio
        addReminderButton.setOnClickListener {
            val intent = Intent(this, AddReminderActivity::class.java)
            startActivity(intent)
        }

        // Programar el Worker para que se ejecute diariamente
        val updateRemindersRequest = PeriodicWorkRequestBuilder<UpdateRemindersWorker>(1, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(this).enqueue(updateRemindersRequest)

        // Escuchar cambios en el campo 'state' de 'closestReminders'
        listenForStateChanges()

        window.statusBarColor = ContextCompat.getColor(this, R.color.colorStatusBar)
    }


    private fun deleteReminder(reminder: Reminder) {
        reminderController.deleteReminder(reminder) {success ->
            if (success) {
                remindersAdapter.removeReminder(reminder)
                Toast.makeText(this, "Recordatorio eliminado exitosamente", Toast.LENGTH_SHORT).show()
                reminderController.fetchAllReminders { remindersList ->
                    updateClosestReminder(remindersList)
                }
            } else {
                Toast.makeText(this, "Error al eliminar el recordatorio", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateClosestReminder(remindersList: List<Reminder>) {
        if (remindersList.isNotEmpty()) {
            val closestReminder = remindersList.first()
            val closestReminderText = "${closestReminder.medicineName}\n ${formatTime(closestReminder.reminderTime)}"
            closestReminderTextView.text = closestReminderText

            val closestDatabaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("closestReminders")
            val closestReminderData = hashMapOf(
                "id" to closestReminder.id,
                "medicineName" to closestReminder.medicineName,
                "reminderTime" to closestReminder.reminderTime,
                "state" to "pending"
            )

            // Almacenar el recordatorio más cercano en la referencia "closestReminders/currentClosest"
            closestDatabaseReference.setValue(closestReminderData)
                .addOnSuccessListener {
                    Log.d(TAG, "Recordatorio más cercano guardado exitosamente.")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error al guardar el recordatorio más cercano", e)
                }

        } else {
            closestReminderTextView.text = "No hay recordatorios"
        }
    }

    private fun formatTime(timeInMillis: Long): String {
        val sdf = SimpleDateFormat("EEE, d 'de' MMM, h:mm a", Locale.getDefault())
        return sdf.format(timeInMillis)
    }

    private fun listenForStateChanges() {
        val closestDatabaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("closestReminders")

        closestDatabaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java)
                val reminderId = snapshot.child("id").getValue(String::class.java)
                Log.d(TAG, "State: $state, Reminder ID: $reminderId") // Añadido para depuración

                if (state == "confirmed") {
                    showMedicationConfirmedNotification()
                    if (reminderId != null) {
                        updatePillsCount(reminderId)
                    } else {
                        Log.w(TAG, "Reminder ID is null")
                    }
                } else if (state == "emergency") {
                    showMedicationEmergencyNotification()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read state value.", error.toException())
            }
        })
    }

    // Función para actualizar la cantidad de píldoras cuando se presiona el boton "confirmed"
    private fun updatePillsCount(reminderId: String) {
        val reminderReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("reminders").child(reminderId)

        reminderReference.child("pills").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentPillsCount = snapshot.getValue(Int::class.java)
                Log.d(TAG, "Current pills count: $currentPillsCount") // Añadido para depuración

                if (currentPillsCount != null) {

                    // Verificar si quedan pocos medicamentos
                    if (currentPillsCount < 3 && currentPillsCount > 1) {
                        sendLowPillsNotification()
                    }

                    // Si quedan medicametnos, actualizar el conteo
                    if (currentPillsCount > 0) {
                        val  newPillsCount = currentPillsCount - 1
                        Log.d(TAG, "New pills count: $newPillsCount") // Log para depuración

                        reminderReference.child("pills").setValue(newPillsCount)
                            .addOnSuccessListener {
                                Log.d(TAG, "Cantidad de medicamentos actualizada correctamente.")

                                // Verificar si se agotaron los medicamentos
                                if (newPillsCount == 0) {
                                    sendOutOfPillsNotification()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "Error al actualizar la cantidad de medicamentos", e)
                            }
                    } else {
                        Log.w(TAG, "No quedan medicamentos disponibles para actualizar.")
                    }

                } else {
                    Log.w(TAG, "currentPillsCount is null")
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error al leer la cantidad de píldoras", error.toException())
            }
        } )
    }

    private fun showMedicationConfirmedNotification() {
        Toast.makeText(this, "¡El usuario confirmo la toma del medicamento!", Toast.LENGTH_SHORT).show()
    }

    private fun showMedicationEmergencyNotification() {
        Toast.makeText(this, "¡El usuario presionó el botón de emergencia!", Toast.LENGTH_SHORT).show()
    }

    private fun sendLowPillsNotification() {
        Toast.makeText(this, "Quedan POCOS medicamentos disponibles para este recordatorio", Toast.LENGTH_SHORT).show()
    }

    private fun sendOutOfPillsNotification() {
        Toast.makeText(this, "NO quedan medicamentos disponibles para este recordatorio", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }

}