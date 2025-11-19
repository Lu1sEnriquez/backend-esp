package com.api.plant.config;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence; // 1. IMPORTACIÓN NECESARIA
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class MqttConfig {

    // --- 1. INYECCIÓN DE VARIABLES DE ENTORNO ---
    @Value("${mqtt.backend.username}")
    private String backendUsername;

    @Value("${mqtt.backend.password}")
    private String backendPassword;

    // --- NUEVA INYECCIÓN DEL CLIENT ID ESTATICO ---
    @Value("${mqtt.backend.client-id}")
    private String backendClientId;
    // --- 2. INYECCIÓN DEL DIRECTORIO DE PERSISTENCIA ---
    @Value("${paho.persistence.dir}")
    private String pahoPersistenceDir;
    // ------------------------------------------

    /**
     * MDTODO AUXILIAR: Crea una nueva instancia de MqttClient con la URI especificada.
     * Implementa la persistencia de archivos controlada.
     */
    public MqttClient createNewMqttClient(String brokerUrl) throws MqttException {

        // 1. Usar el ID fijo inyectado en lugar de un UUID aleatorio
        String clientId = backendClientId;
        // 2. CREACIÓN DEL OBJETO DE PERSISTENCIA CON LA RUTA FIJA
        MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence(pahoPersistenceDir);

        // 3. Se pasa el objeto de persistencia al constructor del MqttClient
        return new MqttClient(brokerUrl, clientId, persistence);
    }

    /**
     * Bean para las opciones de conexión (Añadir credenciales).
     */
    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(true);

        // --- ASIGNACIÓN DE CREDENCIALES DESDE VARIABLES DE ENTORNO ---
        options.setUserName(backendUsername);
        options.setPassword(backendPassword.toCharArray());
        // -----------------------------------------------------------

        return options;
    }
}