package com.api.plant.service;

import com.api.plant.dto.command.DeviceCommand; // Enum de comandos
import com.api.plant.dto.command.GenericCommandPayload; // DTO genérico
import com.api.plant.entity.MqttBroker;
import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.MqttBrokerRepository;
import com.api.plant.repository.PlantDeviceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper; // Serializador
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class ActuatorService {

    private static final Logger log = LoggerFactory.getLogger(ActuatorService.class);

    // Dependencias
    private final MqttDiscoveryService mqttDiscoveryService;
    private final PlantDeviceRepository plantDeviceRepository;
    private final MqttBrokerRepository brokerRepository;
    private final MqttTopicService mqttTopicService; // Nuevo
    private final ObjectMapper objectMapper;         // Nuevo

    // Constructor con todas las dependencias
    public ActuatorService(MqttDiscoveryService mqttDiscoveryService,
                           PlantDeviceRepository plantDeviceRepository,
                           MqttBrokerRepository brokerRepository,
                           MqttTopicService mqttTopicService,
                           ObjectMapper objectMapper) {
        this.mqttDiscoveryService = mqttDiscoveryService;
        this.plantDeviceRepository = plantDeviceRepository;
        this.brokerRepository = brokerRepository;
        this.mqttTopicService = mqttTopicService;
        this.objectMapper = objectMapper;
    }

    /**
     * Metodo generico que recibe un comando DTO y lo envía a la planta.
     * Este metodo centraliza la lógica de publicación para todos los comandos.
     * @param plantId El ID del dispositivo (MQTT Username).
     * @param payloadObject El DTO con el comando y sus parámetros.
     */
    public void sendCommand(String plantId, GenericCommandPayload payloadObject) throws Exception {

        // 1. Obtener el cliente específico y verificar la conexión
        MqttClient client = getClientForPlant(plantId);

        // 2. Obtener el tópico (debe ser genérico o específico según el comando)
        // Usaremos el tópico de comandos de riego/actuación como default,
        // pero se podría usar otro tópico basado en el payloadObject.command() si fuera necesario.
        String commandTopic = mqttTopicService.getDeviceCommandTopic(plantId);

        // 3. Serializar y publicar
        publishCommand(client, commandTopic, payloadObject);

        log.info("📢 Comando {} enviado con éxito a la planta {}.", payloadObject.command().name(), plantId);
    }




    // --- MÉTODOS AUXILIARES PRIVADOS ---

    /**
     * Resuelve el MqttClient correcto para un plantId (lógica de multiconexión).
     */
    private MqttClient getClientForPlant(String plantId) throws Exception {

        PlantDevice device = plantDeviceRepository.findByPlantId(plantId)
                .orElseThrow(() -> {
                    log.error("Dispositivo no encontrado con PlantId: {}", plantId);
                    return new IllegalStateException("Dispositivo no encontrado.");
                });

        String brokerId = device.getBrokerId(); // Usamos la propiedad asociada

        if (brokerId == null || brokerId.isEmpty()) {
            throw new IllegalStateException("El dispositivo no tiene un BrokerId asociado.");
        }

        // Resuelve el BrokerId a la URL dinámica (resiliencia ngrok)
        MqttBroker broker = brokerRepository.findById(brokerId)
                .orElseThrow(() -> {
                    log.error("Broker no encontrado con ID: {}", brokerId);
                    return new IllegalStateException("El Broker asociado (ID: " + brokerId + ") no fue encontrado.");
                });

        String brokerUrl = "tcp://" + broker.getUrl() + ":" + broker.getPort();

        // Obtiene el cliente activo del mapa (multi-broker)
        MqttClient client = mqttDiscoveryService.getClientByUrl(brokerUrl);

        if (client == null) {
            throw new IllegalStateException("El Broker asociado (" + brokerUrl + ") a la planta está inactivo.");
        }
        return client;
    }

    /**
     * Serializa y publica un GenericCommandPayload a un tópico.
     */
    private void publishCommand(MqttClient client, String topic, GenericCommandPayload payloadObject) throws Exception {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(payloadObject);
        } catch (JsonProcessingException e) {
            log.error("❌ Error fatal al serializar el payload del comando: {}", payloadObject.command().name(), e);
            throw new RuntimeException("Error de serialización del payload.", e);
        }

        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);

        client.publish(topic, message);
        log.info("Comando {} publicado. Tópico: {}, Payload: {}", payloadObject.command().name(), topic, payload);
    }
}