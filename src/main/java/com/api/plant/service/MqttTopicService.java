package com.api.plant.service;

import org.springframework.stereotype.Service;

@Service
public class MqttTopicService {

    // --- CONSTANTES PARA TÓPICOS ---
    private static final String PLANT_PREFIX = "planta";
    private static final String LECTURAS_SUFFIX = "lecturas";
    private static final String COMMAND_SUFFIX = "command";
    private static final String GENERAL = "general";
    private static final String TOPIC_SEPARATOR = "/";
    private static final String WILDCARD = "#";

    /**
     * Tópico de suscripción Wildcard (ej: "planta/#")
     */
    public String getWildcardSubscriptionTopic() {
        return PLANT_PREFIX + TOPIC_SEPARATOR + WILDCARD;
    }

    /**
     * Tópico de datos de una planta (ej: "planta/PNT-123/lecturas")
     */
    public String getDeviceDataTopic(String plantId) {
        return String.join(TOPIC_SEPARATOR, PLANT_PREFIX, plantId, LECTURAS_SUFFIX);
    }

    /**
     * Tópico de comandos de una planta (ej: "planta/PNT-123/command/")
     */
    public String getDeviceCommandTopic(String plantId) {
        return String.join(TOPIC_SEPARATOR, PLANT_PREFIX, plantId, COMMAND_SUFFIX, "");
    }

    /**
     * Tópico general por defecto para inicializar el cliente de salida.
     * (ej: "planta/general/command")
     */
    public String getGeneralCommandTopic() {
        return String.join(TOPIC_SEPARATOR, PLANT_PREFIX, GENERAL, COMMAND_SUFFIX);
    }

    /**
     * Tópico WebSocket para una planta específica
     */
    public String getWebSocketTopic(String plantId) {
        return "/topic/plant/" + plantId;
    }
}