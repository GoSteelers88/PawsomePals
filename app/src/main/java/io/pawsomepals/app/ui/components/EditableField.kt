package io.pawsomepals.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun EditableField(label: String, initialValue: String, onValueChange: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var currentValue by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ", style = MaterialTheme.typography.bodyLarge)
        if (isEditing) {
            TextField(
                value = currentValue,
                onValueChange = { currentValue = it },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                onValueChange(currentValue)
                isEditing = false
            }) {
                Icon(Icons.Default.Check, contentDescription = "Save")
            }
        } else {
            Text(currentValue, modifier = Modifier.weight(1f))
            IconButton(onClick = { isEditing = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }
    }
}