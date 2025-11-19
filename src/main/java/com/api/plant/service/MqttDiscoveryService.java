package com.api.plant.service;

import com.api.plant.config.MqttConfig;
import com.api.plant.entity.MqttBroker;
import com.api.plant.repository.MqttBrokerRepository;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext; // Para obtener el bean de Ingesta
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MqttDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(MqttDiscoveryService.class);

    // --- Dependencias Finales (Inyectadas por Constructor) ---
    private final MqttBrokerRepository brokerRepository;
    private final MqttConnectOptions mqttConnectOptions;
    private final MqttConfig mqttConfig;
    private final MqttTopicService mqttTopicService;
    private final ApplicationContext applicationContext; // Para obtener el bean de Ingesta

    private final Map<String, MqttClient> activeClients = new ConcurrentHashMap<>();

    // --- Constructor Único (Inyección Limpia) ---
    public MqttDiscoveryService(MqttBrokerRepository brokerRepository,
                                MqttConnectOptions mqttConnectOptions,
                                MqttConfig mqttConfig,
                                MqttTopicService mqttTopicService,
                                ApplicationContext applicationContext) {
        this.brokerRepository = brokerRepository;
        this.mqttConnectOptions = mqttConnectOptions;
        this.mqttConfig = mqttConfig;
        this.mqttTopicService = mqttTopicService;
        this.applicationContext = applicationContext;
    }
    /**
     * Tarea programada: Consulta Mongo periódicamente (cada 30 segundos)
     * para encontrar TODOS los brokers activos y gestionar sus conexiones.
     */
    @Scheduled(fixedRateString = "${scheduler.discovery.rate-ms:30000}")
    public void discoverAndConnect() {
        log.info("🔎 Ejecutando Tarea Programada de Descubrimiento de Brokers MQTT...");

        // 1. Obtener TODOS los brokers activos (asumimos findByIsActiveTrue devuelve una lista)
        List<MqttBroker> activeBrokers = brokerRepository.findByIsActiveTrue();

        // 2. Identificar URLs válidas (las que deberían estar activas)
        List<String> validBrokerUrls = activeBrokers.stream()
                .map(this::getBrokerUrl)
                .collect(Collectors.toList());

        // 3. Revisar clientes existentes (Desconectar y eliminar los que ya no están activos)
        activeClients.keySet().removeIf(url -> {
            MqttClient client = activeClients.get(url);
            if (!validBrokerUrls.contains(url)) {
                disconnectAndCloseClient(client, "Broker inactivo o URL cambiada.");
                return true; // Elimina esta entrada del mapa
            }
            return false;
        });

        // 4. Conectar o Reconectar clientes necesarios
        for (MqttBroker broker : activeBrokers) {
            String brokerUrl = getBrokerUrl(broker);
            // ... (Lógica de if client == null) ...

            try {
                MqttClient newClient = mqttConfig.createNewMqttClient(brokerUrl);

                // --- (Lógica de CustomMqttCallback) ---
                MqttIngestionService ingestionService = applicationContext.getBean(MqttIngestionService.class);
                MqttCallback customCallback = new CustomMqttCallback(brokerUrl, ingestionService);
                newClient.setCallback(customCallback);
                // ------------------------------------

                newClient.connect(mqttConnectOptions);

                // --- ¡LA CORRECCIÓN ESTÁ AQUÍ! ---
                // 1. Suscripción a datos de plantas (la que ya tenías)
                newClient.subscribe(mqttTopicService.getWildcardSubscriptionTopic(), 1);

                // 2. Suscripción a tópicos de control (LA QUE FALTABA)
                newClient.subscribe(mqttTopicService.getWildcardControlTopic(), 1);
                // ------------------------------------

                activeClients.put(brokerUrl, newClient);
                log.info("✅ Conexión MQTT exitosa a: {}. Suscrito a planta/# y control/#", brokerUrl);

            } catch (MqttException e) {
                // ... (error handling) ...
            }
        }
        // Manejo del caso en que todos los brokers se desactivan
        if (activeBrokers.isEmpty() && !activeClients.isEmpty()) {
            log.warn("⚠️ No se encontró ningún Broker activo. Cerrando todas las conexiones MQTT.");
            activeClients.values().forEach(client -> disconnectAndCloseClient(client, "No hay brokers activos en DB."));
            activeClients.clear();
        }
    }

    // Método auxiliar para construir la URL
    private String getBrokerUrl(MqttBroker broker) {
        return "tcp://" + broker.getUrl() + ":" + broker.getPort();
    }

    // Método auxiliar para desconectar y cerrar
    private void disconnectAndCloseClient(MqttClient client, String reason) {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
                log.debug("Desconexión y cierre exitoso. Razón: {}", reason);
            } catch (MqttException e) {
                log.error("Error al desconectar/cerrar el cliente: {}", e.getMessage());
            }
        }
    }

    /**
     * Método auxiliar para que el ActuatorService obtenga el MqttClient
     * asociado a una URL específica (previamente resuelta por el BrokerId).
     * @param brokerUrl La URL completa del broker.
     * @return El MqttClient conectado, o null si no se encuentra o no está conectado.
     */
    public MqttClient getClientByUrl(String brokerUrl) {
        MqttClient client = activeClients.get(brokerUrl);
        return (client != null && client.isConnected()) ? client : null;
    }


    /**
     * Método auxiliar para obtener CUALQUIER cliente activo disponible.
     * Utilizado por servicios como DeviceProvisioningService para enviar comandos
     * cuando la URL específica del broker aún no es conocida o es un comando de administración.
     * @return Un Optional que contiene un MqttClient activo, o vacío si no hay ninguno.
     */
    public Optional<MqttClient> getAnyActiveMqttClient() {
        // Busca el primer cliente conectado en el mapa de activeClients.
        return activeClients.values().stream()
                .filter(MqttClient::isConnected)
                .findFirst();
    }
}