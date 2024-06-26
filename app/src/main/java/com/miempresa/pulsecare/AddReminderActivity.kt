package com.miempresa.pulsecare

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipDescription
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException

class AddReminderActivity : AppCompatActivity() {

    private lateinit var reminderController: ReminderController
    private lateinit var medicineNameEditText: EditText
    private lateinit var pillsEditText: EditText
    private lateinit var descriptionEditText: EditText
    private lateinit var addReminderButton: Button
    private lateinit var cancelReminderButton: Button
    private lateinit var setDateTimeButton: Button

    private lateinit var checkBoxSunday: CheckBox
    private lateinit var checkBoxMonday: CheckBox
    private lateinit var checkBoxTuesday: CheckBox
    private lateinit var checkBoxWednesday: CheckBox
    private lateinit var checkBoxThursday: CheckBox
    private lateinit var checkBoxFriday: CheckBox
    private lateinit var checkBoxSaturday: CheckBox

    private lateinit var calendar: Calendar
    private var reminderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_reminder)

        // Inicializar vistas
        medicineNameEditText = findViewById(R.id.editTextMedicineName)
        descriptionEditText = findViewById(R.id.editTextDescription)
        pillsEditText = findViewById(R.id.editTextPills)
        addReminderButton = findViewById(R.id.buttonSetReminder)
        cancelReminderButton = findViewById(R.id.buttonCancelReminder)
        setDateTimeButton = findViewById(R.id.buttonSetDateTime)

        checkBoxSunday = findViewById(R.id.checkBoxSunday)
        checkBoxMonday = findViewById(R.id.checkBoxMonday)
        checkBoxTuesday = findViewById(R.id.checkBoxTuesday)
        checkBoxWednesday = findViewById(R.id.checkBoxWednesday)
        checkBoxThursday = findViewById(R.id.checkBoxThursday)
        checkBoxFriday = findViewById(R.id.checkBoxFriday)
        checkBoxSaturday = findViewById(R.id.checkBoxSaturday)

        // Inicializar instancia de calendario
        calendar = Calendar.getInstance()

        // Inicializar controlador
        reminderController = ReminderController(this)

        // Obtener datos del intent si es una edición
        reminderId = intent.getStringExtra("reminder_id")
        val medicineName = intent.getStringExtra("medicine_name")
        val reminderTime = intent.getLongExtra("reminder_time", -1)
        val reminderDescription = intent.getStringExtra("reminder_description")
        val pills = intent.getIntExtra("pills", 0)
        val repeatDays = intent.getIntArrayExtra("repeat_days")?.toList() ?: emptyList()

        if (reminderId != null) {
            medicineNameEditText.setText(medicineName)
            calendar.timeInMillis = reminderTime
            descriptionEditText.setText(reminderDescription)
            pillsEditText.setText(pills.toString())
            // Marcar los días de repetición
            repeatDays.forEach { day ->
                when (day) {
                    Calendar.SUNDAY -> checkBoxSunday.isChecked = true
                    Calendar.MONDAY -> checkBoxMonday.isChecked = true
                    Calendar.TUESDAY -> checkBoxTuesday.isChecked = true
                    Calendar.WEDNESDAY -> checkBoxWednesday.isChecked = true
                    Calendar.THURSDAY -> checkBoxThursday.isChecked = true
                    Calendar.FRIDAY -> checkBoxFriday.isChecked = true
                    Calendar.SATURDAY -> checkBoxSaturday.isChecked = true
                }
            }
        }

        setDateTimeButton.setOnClickListener {
            showDateTimePickerDialog()
        }

        cancelReminderButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        addReminderButton.setOnClickListener {
            val newMedicineName = medicineNameEditText.text.toString().trim()
            if (newMedicineName.isNotEmpty()) {
                val newRepeatDays = getSelectedRepeatDays()
                val currentTimeInMillis = Calendar.getInstance().timeInMillis
                var currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                var reminderTime = calendar.timeInMillis

                // Verificar si el día actual está seleccionado para la repetición
                if (newRepeatDays.contains(currentDay)) {
                    // Verificar si el tiempo actual es después del tiempo del recordatorio actual
                    while (System.currentTimeMillis() > reminderTime) {
                        // Sumar un día al tiempo actual hasta que sea después del tiempo del recordatorio
                        calendar.add(Calendar.DATE, 1)
                        reminderTime = calendar.timeInMillis
                    }
                }

                // Actualizar el reminderTime con el nuevo tiempo calculado
                reminderTime = calendar.timeInMillis

                val newReminderDescription = descriptionEditText.text.toString().trim()

                // Convertir el texto ingresado en el EditText de pastillas a un Int
                val newPillsText = pillsEditText.text.toString().trim()
                val newPills = if (newPillsText.isNotEmpty()) newPillsText.toInt() else 0

                val reminder = Reminder(newMedicineName, reminderTime, newRepeatDays, newReminderDescription, newPills, reminderId)
                if (reminderId == null) {
                    reminderController.addReminder(reminder) { success ->
                        if (success) {
                            finish() // Cerrar actividad si se guardó correctamente
                        } else {
                            Toast.makeText(this, "Error al agregar el recordatorio", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    reminderController.updateReminder(reminder) { success ->
                        if (success) {
                            finish() // Cerrar actividad si se actualizó correctamente
                        } else {
                            Toast.makeText(this, "Error al actualizar el recordatorio", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Ingrese un nombre para el medicamento", Toast.LENGTH_SHORT).show()
            }

        }

        window.statusBarColor = ContextCompat.getColor(this, R.color.colorStatusBar)
    }


    private fun showDateTimePickerDialog() {
        val currentDate = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            R.style.MyTimePickerDialogTheme, // Aplicar el estilo personalizado
            { _, year, month, dayOfMonth ->
                // Establecer la fecha seleccionada en el calendario
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePickerDialog()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Restringir fechas pasadas en DatePickerDialog
        datePickerDialog.datePicker.minDate = currentDate.timeInMillis
        datePickerDialog.show()
    }

    private fun showTimePickerDialog() {
        val currentTime = Calendar.getInstance()

        val timePickerDialog = TimePickerDialog(
            this,
            R.style.MyTimePickerDialogTheme,
            { _, hourOfDay, minute ->
                // Establecer la hora seleccionada en el calendario
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )

        // Restringir horas pasadas si la fecha seleccionada es hoy
        if (calendar.get(Calendar.YEAR) == currentTime.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == currentTime.get(Calendar.DAY_OF_YEAR)) {
            // Actualizar el TimePickerDialog para que no permita seleccionar horas pasadas
            timePickerDialog.updateTime(currentTime.get(Calendar.HOUR), currentTime.get(Calendar.MINUTE))
        }

        timePickerDialog.show()
    }

    private fun getSelectedRepeatDays(): List<Int> {
        val repeatDays = mutableListOf<Int>()

        if (checkBoxSunday.isChecked) repeatDays.add(Calendar.SUNDAY)
        if (checkBoxMonday.isChecked) repeatDays.add(Calendar.MONDAY)
        if (checkBoxTuesday.isChecked) repeatDays.add(Calendar.TUESDAY)
        if (checkBoxWednesday.isChecked) repeatDays.add(Calendar.WEDNESDAY)
        if (checkBoxThursday.isChecked) repeatDays.add(Calendar.THURSDAY)
        if (checkBoxFriday.isChecked) repeatDays.add(Calendar.FRIDAY)
        if (checkBoxSaturday.isChecked) repeatDays.add(Calendar.SATURDAY)

        return repeatDays
    }
}