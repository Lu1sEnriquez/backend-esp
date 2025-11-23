package com.api.plant.service;

import org.springframework.stereotype.Service;

@Service
public class MqttTopicService {

    // --- PREFIJOS CENTRALIZADOS ---
    private static final String PLANT_PREFIX = "planta";
    private static final String CONTROL_PREFIX = "control/provisioning";

    /**
     * Tópico de suscripción Wildcard (ej: "planta/#")
     */
    public String getWildcardSubscriptionTopic() {
        return String.format("%s/#", PLANT_PREFIX);
    }

    /**
     * Tópico de datos de una planta (ej: "planta/PNT-123/lecturas")
     */
    public String getDeviceDataTopic(String plantId) {
        return String.format("%s/%s/lecturas", PLANT_PREFIX, plantId);
    }

    /**
     * Tópico de comandos de una planta (ej: "planta/PNT-123/command/")
     */
    public String getDeviceCommandTopic(String plantId) {
        return String.format("%s/%s/command/", PLANT_PREFIX, plantId);
    }

    /**
     * Tópico general por defecto para inicializar el cliente de salida.
     * (ej: "planta/general/command")
     */
    public String getGeneralCommandTopic() {
        return String.format("%s/general/command", PLANT_PREFIX);
    }

    // --- Tópicos de Provisioning ---

    public String getDiscoveryTopic() {
        return String.format("%s/discovery", CONTROL_PREFIX);
    }

    public String getProvisioningConfigTopic(String macAddress) {
        return String.format("%s/device/%s", CONTROL_PREFIX, macAddress);
    }

    public String getWildcardControlTopic() {
        return String.format("%s/#", CONTROL_PREFIX);
    }
}