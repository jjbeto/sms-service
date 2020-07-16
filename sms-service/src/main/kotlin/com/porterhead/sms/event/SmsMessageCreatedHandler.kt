package com.porterhead.sms.event

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.porterhead.sms.domain.MessageStatus
import com.porterhead.sms.jpa.MessageRepository
import mu.KotlinLogging
import java.time.Instant
import java.util.*
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import javax.transaction.Transactional

/**
 * Handle events that have been generated by the Debezium connector
 */
@ApplicationScoped
class SmsMessageCreatedHandler {

    companion object {
        private val mapper: ObjectMapper = ObjectMapper()
    }

    private val log = KotlinLogging.logger {}

    @Inject
    lateinit var eventLog: EventLog

    @Inject
    lateinit var messageRepository: MessageRepository

    @Transactional
    fun onEvent(eventId: UUID, eventType: String, key: String, payload: String, ts: Instant) {
        log.debug { "Event handler for Created messages invoked" }
        if (eventLog.alreadyProcessed(eventId)) {
            log.warn { "Message had already been handled $eventId" }
            return
        }
        val eventPayload = deserialize(payload)
        val messageId = UUID.fromString(eventPayload.get("id").asText())
        val message = messageRepository.findById(messageId)
        message.status = MessageStatus.DELIVERED
        message.updatedAt = Instant.now()
        messageRepository.persist(message)
        eventLog.processed(eventId)
        log.debug { "Message has been processed" }

    }

    private fun deserialize(eventMessage: String): JsonNode {
        log.debug { "attempting to deserialize message $eventMessage" }
        var payload = eventMessage //default
        try {
            if (eventMessage.contains("schema")) {
                //extract the payload
                val json: JsonObject = GsonBuilder()
                        .create()
                        .fromJson(eventMessage, JsonObject::class.java)
                payload = json.get("payload").asString
            }
            val unescaped = payload.replace("\\\"", "\"").trimIndent()
            val trimmed = unescaped.removeSurrounding("\"")
            val json: JsonNode = mapper.readTree(trimmed)
            log.debug { "payload has been parsed $json" }
            return json

        } catch (e: Exception) {
            log.error { "Error parsing payload $e" }
            throw e
        }

    }
}
