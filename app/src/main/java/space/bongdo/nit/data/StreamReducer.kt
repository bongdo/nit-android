package space.bongdo.nit.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.serializer

object StreamReducer {
    fun sessionID(event: StreamEvent): String {
        val data = event.data as? JsonObject ?: return ""
        (data["sessionID"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        val form = data["form"] as? JsonObject
        return (form?.get("sessionID") as? JsonPrimitive)?.contentOrNull.orEmpty()
    }

    fun isMessageEvent(type: String): Boolean =
        type.startsWith("session.text.") ||
            type.startsWith("session.reasoning.") ||
            type.startsWith("session.tool.") ||
            type == "session.step.started" ||
            type == "session.step.ended" ||
            type == "session.step.failed"

    fun apply(messages: List<Message>, event: StreamEvent): List<Message> {
        val data = event.data as? JsonObject ?: return messages
        val id = data.string("assistantMessageID") ?: return messages
        val ordinal = data.int("ordinal") ?: 0

        return when (event.type) {
            "session.step.started" -> update(messages, id, event) { message ->
                message.copy(
                    agent = data.string("agent") ?: message.agent,
                    model = message.model,
                    time = message.time.copy(
                        created = message.time.created.takeIf { it > 0 }
                            ?: data.long("started")
                            ?: event.created
                            ?: 0,
                    ),
                )
            }

            "session.text.started" -> update(messages, id, event) {
                upsertText(it, "text", ordinal, data.string("text") ?: "")
            }

            "session.text.delta" -> update(messages, id, event) {
                upsertText(it, "text", ordinal, data.string("delta").orEmpty(), append = true)
            }

            "session.text.ended" -> update(messages, id, event) {
                val full = data.string("text") ?: return@update it
                upsertText(it, "text", ordinal, full)
            }

            "session.reasoning.started" -> update(messages, id, event) {
                upsertText(it, "reasoning", ordinal, data.string("text") ?: "")
            }

            "session.reasoning.delta" -> update(messages, id, event) {
                upsertText(it, "reasoning", ordinal, data.string("delta").orEmpty(), append = true)
            }

            "session.reasoning.ended" -> update(messages, id, event) {
                val full = data.string("text") ?: return@update it
                upsertText(it, "reasoning", ordinal, full)
            }

            "session.tool.input.started" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        part.copy(time = part.time ?: PartTime(created = event.created))
                    }
                }
            }

            "session.tool.input.delta" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        val current = (part.state?.input as? JsonPrimitive)?.contentOrNull.orEmpty()
                        part.withState(status = "streaming", input = JsonPrimitive(current + data.string("delta").orEmpty()))
                    }
                }
            }

            "session.tool.input.ended" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        part.withState(
                            status = "streaming",
                            input = JsonPrimitive(data.string("text") ?: (part.state?.input as? JsonPrimitive)?.contentOrNull.orEmpty()),
                        )
                    }
                }
            }

            "session.tool.called" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        part.copy(
                            executed = data.boolean("executed") ?: part.executed,
                            state = ToolState(
                                status = "running",
                                input = if (part.name == "question") part.state?.input else data["input"],
                                metadata = if (part.name == "question") part.state?.metadata else null,
                            ),
                        )
                    }
                }
            }

            "session.tool.progress" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        part.withState(status = "running", metadata = data["metadata"] ?: part.state?.metadata)
                    }
                }
            }

            "session.tool.success" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        val content = data.list<ToolContent>("content")
                        part.copy(
                            state = ToolState(
                                status = "completed",
                                input = part.state?.input,
                                content = content ?: if (part.name == "question") part.state?.content else emptyList(),
                                metadata = data["metadata"] ?: part.state?.metadata,
                            ),
                        )
                    }
                }
            }

            "session.tool.failed", "session.tool.error" -> {
                val toolID = data.string("id") ?: return messages
                update(messages, id, event) {
                    upsertTool(it, toolID, data.string("name") ?: "tool") { part ->
                        val content = data.list<ToolContent>("content")
                        part.copy(
                            state = ToolState(
                                status = "error",
                                input = part.state?.input,
                                content = content ?: part.state?.content,
                                metadata = data["metadata"] ?: part.state?.metadata,
                                error = data["error"]?.let { element ->
                                    runCatching {
                                        val error = element.jsonObject
                                        ToolError(
                                            name = error.string("name"),
                                            message = error.string("message"),
                                        )
                                    }.getOrNull()
                                } ?: ToolError(message = "инструмент завершился с ошибкой"),
                            ),
                        )
                    }
                }
            }

            "session.step.ended" -> update(messages, id, event) { message ->
                message.copy(
                    finish = data.string("finish") ?: message.finish,
                    cost = data.double("cost") ?: message.cost,
                    tokens = data["tokens"]?.let { runCatching { Json.decodeTokens(it) }.getOrNull() } ?: message.tokens,
                )
            }

            "session.step.failed" -> update(messages, id, event) { message ->
                message.copy(
                    error = data["error"]?.let { element ->
                        runCatching {
                            val error = element.jsonObject
                            MessageError(
                                name = error.string("name"),
                                message = error.string("message"),
                                type = error.string("type"),
                                status = error.int("status"),
                                retryAfterMs = error.long("retryAfterMs"),
                            )
                        }.getOrNull()
                    } ?: MessageError(message = "шаг завершился с ошибкой"),
                )
            }

            else -> messages
        }
    }

    private fun update(
        messages: List<Message>,
        id: String,
        event: StreamEvent,
        change: (Message) -> Message,
    ): List<Message> {
        val index = messages.indexOfFirst { it.id == id }
        return if (index < 0) {
            val base = Message(
                id = id,
                type = "assistant",
                time = MessageTime(created = event.created ?: System.currentTimeMillis()),
                content = emptyList(),
            )
            messages + change(base)
        } else {
            messages.toMutableList().also { it[index] = change(it[index]) }
        }
    }

    private fun upsertText(
        message: Message,
        kind: String,
        ordinal: Int,
        value: String,
        append: Boolean = false,
    ): Message {
        val content = (message.content ?: emptyList()).toMutableList()
        val index = content.indexOfFirst { it.type == kind && (it.ordinal ?: 0) == ordinal }
        if (index >= 0) {
            val part = content[index]
            content[index] = part.copy(text = if (append) part.text + value else value)
        } else {
            content += Part(type = kind, text = value, ordinal = ordinal)
        }
        return message.copy(content = content)
    }

    private fun upsertTool(
        message: Message,
        id: String,
        name: String,
        change: (Part) -> Part,
    ): Message {
        val content = (message.content ?: emptyList()).toMutableList()
        val index = content.indexOfFirst { it.type == "tool" && it.id == id }
        if (index >= 0) {
            content[index] = change(content[index])
        } else {
            content += change(
                Part(type = "tool", id = id, name = name, state = ToolState(status = "streaming")),
            )
        }
        return message.copy(content = content)
    }

    private fun Part.withState(
        status: String,
        input: JsonElement? = state?.input,
        metadata: JsonElement? = state?.metadata,
    ): Part = copy(state = ToolState(status = status, input = input, metadata = metadata, content = state?.content, error = state?.error))

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private inline fun <reified T> JsonObject.list(key: String): List<T>? {
        val element = this[key] ?: return null
        return runCatching { Json.decodeFromJsonElement<List<T>>(element) }.getOrNull()
    }

    private object Json {
        private val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        inline fun <reified T> decodeFromJsonElement(element: JsonElement): T =
            json.decodeFromJsonElement(serializer<T>(), element)

        fun decodeTokens(element: JsonElement): Tokens? =
            runCatching { json.decodeFromJsonElement(Tokens.serializer(), element) }.getOrNull()
    }
}
