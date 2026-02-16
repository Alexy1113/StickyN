package com.example.stickyn

import android.app.Dialog
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class RestoreActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private var notesList = mutableListOf<Note>()
    private lateinit var adapter: RestoreAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restore_notes)

        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        val height = (resources.displayMetrics.heightPixels * 0.8).toInt()
        window.setLayout(width, height)

        recyclerView = findViewById(R.id.recycler_restore)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.button_close_restore).setOnClickListener {
            finish()
        }

        loadNotes()
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            val notes = AppDatabase.getDatabase(this@RestoreActivity).noteDao().getDeletedNotes()
            notesList.clear()
            notesList.addAll(notes)
            adapter = RestoreAdapter(notesList, 
                onRestoreClick = { note, position -> 
                    restoreWidget(this@RestoreActivity, note.id)
                    fadeAwayItem(position)
                },
                onDeleteClick = { note -> showDeleteConfirmation(note) }
            )
            recyclerView.adapter = adapter
        }
    }

    private fun fadeAwayItem(position: Int) {
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        if (viewHolder != null) {
            viewHolder.itemView.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    if (position < notesList.size) {
                        notesList.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        adapter.notifyItemRangeChanged(position, notesList.size)
                    }
                }
                .start()
        } else {
            // Fallback if view is not visible or already recycled
            if (position < notesList.size) {
                notesList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, notesList.size)
            }
        }
    }

    private fun showDeleteConfirmation(note: Note) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_delete)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnDelete = dialog.findViewById<MaterialButton>(R.id.button_confirm_delete)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.button_cancel_delete)

        btnDelete.setOnClickListener {
            deletePermanently(note)
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deletePermanently(note: Note) {
        lifecycleScope.launch {
            AppDatabase.getDatabase(this@RestoreActivity).noteDao().delete(note)
            val index = notesList.indexOf(note)
            if (index != -1) {
                fadeAwayItem(index)
                Toast.makeText(this@RestoreActivity, "Note deleted permanently", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreWidget(context: Context, noteId: Int) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val myProvider = ComponentName(context, StickyNoteWidget::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val successIntent = Intent(context, WidgetPinnedReceiver::class.java).apply {
                putExtra("NOTE_ID", noteId)
            }

            val successCallback = PendingIntent.getBroadcast(
                context,
                noteId,
                successIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
        } else {
            Toast.makeText(context, "Pinned widgets not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class RestoreAdapter(
        private val notes: List<Note>,
        private val onRestoreClick: (Note, Int) -> Unit,
        private val onDeleteClick: (Note) -> Unit
    ) : RecyclerView.Adapter<RestoreAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val numberText: TextView = view.findViewById(R.id.text_note_number)
            val titleText: TextView = view.findViewById(R.id.text_note_title)
            val snippetText: TextView = view.findViewById(R.id.text_note_snippet)
            val btnRestore: MaterialButton = view.findViewById(R.id.button_restore_note)
            val btnDelete: MaterialButton = view.findViewById(R.id.button_delete_permanent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_restore_note, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val note = notes[position]
            // Reset alpha in case the view was recycled
            holder.itemView.alpha = 1f
            
            holder.numberText.text = "${position + 1}."
            holder.titleText.text = if (note.title.isNotEmpty()) note.title else "No Title"
            
            val spannedContent = Html.fromHtml(note.content, Html.FROM_HTML_MODE_LEGACY)
            val snippet = if (spannedContent.length > 50) spannedContent.subSequence(0, 50).toString() + "..." else spannedContent
            holder.snippetText.text = snippet
            
            holder.btnRestore.setOnClickListener { onRestoreClick(note, holder.adapterPosition) }
            holder.btnDelete.setOnClickListener { onDeleteClick(note) }
        }

        override fun getItemCount(): Int = notes.size
    }
}
