package space.bongdo.nit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import space.bongdo.nit.data.FormField
import space.bongdo.nit.data.FormInfo
import space.bongdo.nit.data.PermissionRequest

@Composable
fun PermissionStrip(requests: List<PermissionRequest>, onReply: (PermissionRequest, String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            requests.forEach { request ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            request.action,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val resources = request.resources.joinToString(", ")
                    if (resources.isNotBlank()) {
                        Text(
                            resources,
                            style = CodeStyle.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onReply(request, "reject") }) { Text("Отклонить") }
                        TextButton(onClick = { onReply(request, "always") }) { Text("Всегда") }
                        Button(
                            onClick = { onReply(request, "once") },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text("Разрешить") }
                    }
                }
            }
        }
    }
}

@Composable
fun FormSheet(
    forms: List<FormInfo>,
    onReply: (FormInfo, JsonObject) -> Unit,
    onCancel: (FormInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(Modifier.heightIn(max = 360.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
            items(forms, key = { it.id }) { form ->
                FormCard(form, onReply, onCancel)
            }
        }
    }
}

@Composable
private fun FormCard(form: FormInfo, onReply: (FormInfo, JsonObject) -> Unit, onCancel: (FormInfo) -> Unit) {
    val state = remember(form.id) {
        val initialValues = mutableStateMapOf<String, String>()
        val initialChecks = mutableStateMapOf<String, Boolean>()
        form.fields.forEach { field ->
            val default = field.default
            if (field.type == "boolean" || field.type == "confirm") {
                initialChecks[field.key] = default?.jsonPrimitive?.booleanOrNull ?: false
            } else {
                default?.jsonPrimitive?.contentOrNull?.let { initialValues[field.key] = it }
            }
        }
        initialValues to initialChecks
    }
    val values = state.first
    val checks = state.second
    var error by remember(form.id) { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(10.dp),
    ) {
        Text(form.title.ifBlank { "Вопрос" }, style = MaterialTheme.typography.titleMedium)
        form.fields.filter { it.visible(values, checks) }.forEach { field ->
            FormFieldView(field, values, checks)
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onCancel(form) }) { Text("Пропустить") }
            Button(
                onClick = {
                    val (answer, problem) = buildAnswer(form, values, checks)
                    if (problem != null) {
                        error = problem
                    } else {
                        error = null
                        onReply(form, answer!!)
                    }
                },
                shape = RoundedCornerShape(8.dp),
            ) { Text("Ответить") }
        }
    }
}

@Composable
private fun FormFieldView(
    field: FormField,
    values: MutableMap<String, String>,
    checks: MutableMap<String, Boolean>,
) {
    if (field.hidden == true) return
    if (field.type == "boolean" || field.type == "confirm") {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(field.title ?: field.key, style = MaterialTheme.typography.bodySmall)
                field.description?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checks[field.key] ?: false, onCheckedChange = { checks[field.key] = it })
        }
        return
    }
    Text(
        field.title ?: field.key,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
    field.description?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (field.type == "select" && !field.options.isNullOrEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            field.options!!.take(4).forEach { option ->
                val selected = values[field.key] == option.value
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                        )
                        .clickable { values[field.key] = option.value }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
        }
    } else {
        OutlinedTextField(
            value = values[field.key].orEmpty(),
            onValueChange = { values[field.key] = it },
            placeholder = (field.placeholder ?: if (field.type == "multiselect") "через запятую" else null)?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall) }
            },
            singleLine = field.type != "textarea",
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (field.type == "number" || field.type == "integer") KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


private fun FormField.visible(values: Map<String, String>, checks: Map<String, Boolean>): Boolean {
    if (hidden == true) return false
    if (conditions.isNullOrEmpty()) return true
    return conditions.all { condition ->
        val actual = checks[condition.key]?.toString() ?: values[condition.key].orEmpty()
        val expected = condition.value?.jsonPrimitive?.contentOrNull.orEmpty()
        if (condition.op == "eq") actual == expected else actual != expected
    }
}

private fun buildAnswer(
    form: FormInfo,
    values: Map<String, String>,
    checks: Map<String, Boolean>,
): Pair<JsonObject?, String?> {
    val answer = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    for (field in form.fields) {
        if (!field.visible(values, checks)) continue
        val raw = values[field.key].orEmpty().trim()
        val boolean = checks[field.key] ?: false
        val isBoolean = field.type == "boolean" || field.type == "confirm"
        if (!isBoolean && raw.isEmpty()) {
            if (field.required == true) return null to "Заполните: ${field.title ?: field.key}"
            continue
        }
        answer[field.key] = when (field.type) {
            "number" -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: continue
            "integer" -> raw.toLongOrNull()?.let { JsonPrimitive(it) } ?: continue
            "boolean", "confirm" -> JsonPrimitive(boolean)
            "multiselect" -> JsonArray(
                raw.split(',').map { JsonPrimitive(it.trim()) }.filter { it.content.isNotBlank() },
            )
            else -> JsonPrimitive(raw)
        }
    }
    return JsonObject(answer) to null
}
