package com.api.plant.config;

import com.api.plant.entity.Reading;
import com.api.plant.service.TelemetryService;
import com.fasterxml.jackson.databind.DeserializationFeature; // <--- IMPORTANTE
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // <--- IMPORTANTE
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    // 1. CONFIGURACIÓN DEL MAPPER (Aquí estaba el error)
    private final ObjectMapper objectMapper = new ObjectMapper()
            // Permite convertir timestamps a java.time.Instant
            .registerModule(new JavaTimeModule())
            // Si el JSON trae campos extra (como "status"), NO lanza error, solo los ignora
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    private TelemetryService telemetryService;

    @Override
    public void handleMessage(Message<?> message) throws MessagingException {
        try {
            String payload = (String) message.getPayload();

            // Log para ver qué llega (útil para depurar)
            // log.info("⚡ Payload crudo: {}", payload);

            // 2. Convertir JSON -> Objeto Java
            // Gracias a la configuración de arriba, esto ya no fallará con "status" ni "Instant"
            Reading reading = objectMapper.readValue(payload, Reading.class);

            // --- FILTRO IMPORTANTE ---
            // Si el JSON era solo {"status":"CONNECTED"}, el objeto Reading se creará
            // pero con plantId nulo. Debemos evitar procesar eso.
            if (reading.getPlantId() == null) {
                // Es un mensaje de control o basura, lo ignoramos silenciosamente
                return;
            }

            // 3. Procesar datos válidos
            if (telemetryService != null) {
                telemetryService.processAndSave(reading);
            }

        } catch (Exception e) {
            log.error("❌ Error procesando mensaje MQTT: {}", e.getMessage());
        }
    }
}