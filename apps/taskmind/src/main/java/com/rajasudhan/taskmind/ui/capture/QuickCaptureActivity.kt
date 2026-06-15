package com.rajasudhan.taskmind.ui.capture

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rajasudhan.taskmind.data.source.CaptureWorker
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme

/**
 * A lightweight, lock-free text capture launched by the Quick Settings tile and the home widget.
 * Shows only an input dialog (no existing data), so it doesn't require unlocking the app.
 */
class QuickCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskMindTheme {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Add to TaskMind") },
                    text = {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Type a note, task or reminder") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(enabled = text.isNotBlank(), onClick = {
                            CaptureWorker.enqueue(applicationContext, "Quick add", text.trim())
                            Toast.makeText(this, "Added to TaskMind for review.", Toast.LENGTH_SHORT).show()
                            finish()
                        }) { Text("Add") }
                    },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancel") } }
                )
            }
        }
    }
}
