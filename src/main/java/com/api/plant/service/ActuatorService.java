package com.api.plant.service;

import com.api.plant.dto.command.GenericCommandPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;

@Service
public class ActuatorService {

    private static final Logger log = LoggerFactory.getLogger(ActuatorService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("mqttOutboundChannel")
    private MessageChannel mqttOutboundChannel;

    @Autowired
    private MqttTopicService mqttTopicService; // <--- INYECCIÓN

    public void sendCommand(String plantId, GenericCommandPayload payload) {

        // USAMOS EL SERVICIO PARA GENERAR EL TÓPICO CORRECTO
        // (planta/{id}/command/)
        String topic = mqttTopicService.getDeviceCommandTopic(plantId);

        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("cmd", payload.command().name());

        String jsonMessage = jsonNode.toString();

        try {
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(jsonMessage)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .setHeader(MqttHeaders.RETAINED, false)
                    .build());

            log.info("📡 Comando enviado: Tópico={}, Payload={}", topic, jsonMessage);

        } catch (Exception e) {
            log.error("❌ Error enviando comando MQTT a {}: {}", topic, e.getMessage());
            throw new IllegalStateException("Error de conexión MQTT.");
        }
    }
}