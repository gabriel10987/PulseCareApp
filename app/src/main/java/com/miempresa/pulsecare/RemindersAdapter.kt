package com.miempresa.pulsecare

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class RemindersAdapter(
    private val onDeleteClick: (Reminder) -> Unit
): RecyclerView.Adapter<RemindersAdapter.ReminderViewHolder>(){

    private var reminders: MutableList<Reminder> = mutableListOf()

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val medicineNameTextView: TextView = itemView.findViewById(R.id.medicineNameTextView)
        val reminderTimeTextView: TextView = itemView.findViewById(R.id.reminderTimeTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.reminderDescriptionTextView)
        val pillsTextView: TextView = itemView.findViewById(R.id.pillsTextView)
        val deleteReminderButton: ImageButton = itemView.findViewById(R.id.deleteReminderButton)
        val toggleReminderSwitch: SwitchCompat = itemView.findViewById(R.id.toggleReminderSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]
        holder.medicineNameTextView.text = reminder.medicineName
        holder.reminderTimeTextView.text = formatTime(reminder.reminderTime)
        holder.descriptionTextView.text = reminder.description
        holder.pillsTextView.text = reminder.pills.toString()

        holder.deleteReminderButton.setOnClickListener {
            onDeleteClick(reminder)
        }

        holder.itemView.setOnClickListener {
            val reminderEditIntent = Intent(holder.itemView.context, AddReminderActivity::class.java).apply {
                putExtra("reminder_id", reminder.id)
                putExtra("medicine_name", reminder.medicineName)
                putExtra("reminder_time", reminder.reminderTime)
                putExtra("reminder_description", reminder.description)
                putExtra("pills", reminder.pills)
                putExtra("repeat_days", reminder.repeatDays.toIntArray())
            }
            holder.itemView.context.startActivity(reminderEditIntent)
        }

    }

    override fun getItemCount(): Int {
        return reminders.size
    }

    fun setData(newReminders: List<Reminder>) {
        reminders.clear()
        reminders.addAll(newReminders)
        notifyDataSetChanged()
    }

    fun removeReminder(reminder: Reminder) {
        val index = reminders.indexOfFirst{it.id == reminder.id}
        if(index != -1) {
            reminders.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}

fun formatTime(timeInMillis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
    return sdf.format(timeInMillis)
}