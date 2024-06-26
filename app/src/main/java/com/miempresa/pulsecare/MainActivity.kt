package com.miempresa.pulsecare

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        reminderController.fetchAllReminders  { remindersList ->
            remindersAdapter.setData(remindersList)
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

        // Logica de Emergencia
        val emergencyStatusReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("emergencyStatus")
        emergencyStatusReference.setValue(EmergencyState(false))
            .addOnSuccessListener {
                Log.d(TAG, "Estado de emergencia inicializado en falso.")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al inicializar el estado de emergencia.", e)
            }

        // Añadir un ValueEventListener para escuchar cambios en el estado de emergencia
        emergencyStatusReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val emergencyStatus = snapshot.getValue(EmergencyState::class.java)
                if (emergencyStatus?.isEmergency == true) {
                    showEmergencyNotification("¡El usuario presionó el botón de emergencia!")
                    resetEmergencyStatusAfterDelay()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Error al escuchar cambios en el estado de emergencia.", error.toException())
            }
        })
    }

    private fun setEmergencyStatus(isEmergency: Boolean) {
        val emergencyStatusReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("emergencyStatus")
        emergencyStatusReference.setValue(EmergencyState(isEmergency))
            .addOnSuccessListener {
                Log.d(TAG, "Estado de emergencia actualizado a $isEmergency.")
                if (isEmergency) {
                    showEmergencyNotification("¡Emergencia activada!")
                    resetEmergencyStatusAfterDelay()
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error al actualizar el estado de emergencia.", e)
            }
    }

    private fun showEmergencyNotification(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun resetEmergencyStatusAfterDelay() {
        val handler = Handler(Looper.getMainLooper())
        val delayMillis: Long = 1 * 60 * 1000 // 1 minuto

        handler.postDelayed({
            setEmergencyStatus(false)
        }, delayMillis)
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

            // Almacenar el recordatorio más cercano en la referencia "closestReminders"
            closestDatabaseReference.setValue(closestReminderData)
                .addOnSuccessListener {
                    Log.d(TAG, "Recordatorio más cercano guardado exitosamente.")
                    scheduleMoveToPendingReminder(closestReminder)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error al guardar el recordatorio más cercano", e)
                }

        } else {
            closestReminderTextView.text = "No hay recordatorios"
        }

    }

    private fun scheduleMoveToPendingReminder(reminder: Reminder) {
        val currentTimeMillis = System.currentTimeMillis()
        val timeDifference = reminder.reminderTime - currentTimeMillis

        if (timeDifference > 0) {
            val moveReminderRequest = OneTimeWorkRequestBuilder<MoveToPendingWorker>()
                .setInitialDelay(timeDifference, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(
                    "reminderId" to reminder.id,
                    "medicineName" to reminder.medicineName,
                    "reminderTime" to reminder.reminderTime
                ))
                .build()

            WorkManager.getInstance(this).enqueue(moveReminderRequest)
        }
    }

    private fun formatTime(timeInMillis: Long): String {
        val sdf = SimpleDateFormat("EEE, d 'de' MMM, h:mm a", Locale.getDefault())
        return sdf.format(timeInMillis)
    }

    private fun listenForStateChanges() {
        val pendingDatabaseReference: DatabaseReference = FirebaseDatabase.getInstance().reference.child("pendingReminders")

        pendingDatabaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (childSnapshot in snapshot.children) {
                    val state = childSnapshot.child("state").getValue(String::class.java)
                    val reminderId = childSnapshot.child("id").getValue(String::class.java)
                    Log.d(TAG, "State: $state, Reminder ID: $reminderId") // Añadido para depuración

                    if (state == "confirmed") {
                        showMedicationConfirmedNotification()
                        if (reminderId != null) {
                            updatePillsCount(reminderId)
                            // Eliminar el recordatorio inmediatamente cuando se confirma
                            val specificPendingReminderReference = pendingDatabaseReference.child(reminderId)

                            specificPendingReminderReference.removeValue()
                                .addOnSuccessListener {
                                    Log.d(TAG, "Recordatorio eliminado de 'pendingReminders' inmediatamente después de la confirmación.")
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "Error al eliminar el recordatorio de 'pendingReminders' inmediatamente después de la confirmación.", e)
                                }
                        } else {
                            Log.w(TAG, "Reminder ID is null")
                        }
                    } else if (state == "emergency") {
                        showMedicationEmergencyNotification()
                    }
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

class MoveToPendingWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val reminderId = inputData.getString("reminderId")
        val medicineName = inputData.getString("medicineName")
        val reminderTime = inputData.getLong("reminderTime", -1)

        if (reminderId != null && medicineName != null && reminderTime != -1L) {
            val pendingDatabaseReference = FirebaseDatabase.getInstance().reference
                .child("pendingReminders")
                .child(reminderId)

            val pendingReminderData = hashMapOf(
                "id" to reminderId,
                "medicineName" to medicineName,
                "reminderTime" to reminderTime,
                "state" to "pending"
            )

            pendingDatabaseReference.setValue(pendingReminderData)
                .addOnSuccessListener {
                    Log.d(TAG, "Recordatorio movido a 'pendingReminders' exitosamente.")
                    // Programar eliminación después de 10 minutos
                    scheduleDeletion(reminderId, 10 * 60 * 1000) // 10 minutos en milisegundos
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error al mover el recordatorio a 'pendingReminders'.", e)
                }

        } else {
            Log.w(TAG, "Error al obtener los datos del recordatorio.")
            return Result.failure()
        }

        return Result.success()
    }

    private fun scheduleDeletion(reminderId: String, delay: Long) {
        val deleteReminderRequest = OneTimeWorkRequestBuilder<DeletePendingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("reminderId" to reminderId))
            .build()

        WorkManager.getInstance(applicationContext).enqueue(deleteReminderRequest)
    }
}

class DeletePendingReminderWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val reminderId = inputData.getString("reminderId")

        if (reminderId != null) {
            val pendingDatabaseReference = FirebaseDatabase.getInstance().reference
                .child("pendingReminders")
                .child(reminderId)

            pendingDatabaseReference.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val state = snapshot.child("state").getValue(String::class.java)
                    // Eliminar el recordatorio sin importar el estado
                    pendingDatabaseReference.removeValue()
                        .addOnSuccessListener {
                            if (state == "confirmed") {
                                Toast.makeText(applicationContext, "El recordatorio ha sido confirmado y eliminado.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(applicationContext, "El recordatorio no ha sido confirmado pero ha sido eliminado.", Toast.LENGTH_SHORT).show()
                            }
                            Log.d(TAG, "Recordatorio eliminado de 'pendingReminders'.")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error al eliminar el recordatorio de 'pendingReminders'.", e)
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Error al leer el estado del recordatorio.", error.toException())
                }
            })
        } else {
            Log.w(TAG, "Error al obtener el ID del recordatorio.")
            return Result.failure()
        }

        return Result.success()
    }
}