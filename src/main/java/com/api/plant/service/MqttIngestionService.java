package com.api.plant.service;

import com.api.plant.entity.PlantDevice;
import com.api.plant.entity.Reading;
import com.api.plant.repository.PlantDeviceRepository;
import com.api.plant.repository.ReadingRepository;
import org.eclipse.paho.client.mqttv3.MqttMessage; // Importante
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

@Service
public class MqttIngestionService { // ¡YA NO IMPLEMENTA MqttCallback!

    private static final Logger log = LoggerFactory.getLogger(MqttIngestionService.class);

    // Repositorios y Servicios Centrales
    private final PlantDeviceRepository deviceRepository;
    private final ReadingRepository readingRepository;
    private final QCLayerService qcLayerService;
    private final AdvisorService advisorService;
    private final DeviceProvisioningService provisioningService;
    private final MqttTopicService mqttTopicService;
    // El ObjectMapper es inyectado en QCLayerService, no es necesario aquí.

    // Constructor con Inyección de Dependencias
    public MqttIngestionService(PlantDeviceRepository deviceRepository,
                                ReadingRepository readingRepository,
                                QCLayerService qcLayerService,
                                AdvisorService advisorService,
                                DeviceProvisioningService provisioningService,
                                MqttTopicService mqttTopicService) {
        this.deviceRepository = deviceRepository;
        this.readingRepository = readingRepository;
        this.qcLayerService = qcLayerService;
        this.advisorService = advisorService;
        this.provisioningService = provisioningService;
        this.mqttTopicService = mqttTopicService;
    }

    /**
     * METODO DE ENTRADA PRINCIPAL.
     * Llamado por CustomMqttCallback (que sabe de qué broker vino).
     *
     * @param brokerUrl La URL del broker que recibió este mensaje.
     * @param topic     El tópico del mensaje.
     * @param message   El payload MQTT.
     */
    public void handleMessage(String brokerUrl, String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

        // --- LOG DE ENTRADA CRÍTICO ---
        log.info("INGESTION: Mensaje recibido en Broker [{}], Tópico [{}]", brokerUrl, topic);

        try {
            // 1. Manejo de Tópicos de Control/Provisioning
            // Usamos startsWith para enrutar el mensaje
            if (topic.startsWith(mqttTopicService.getDiscoveryTopic())) {

                log.info("INGESTION: Tópico de descubrimiento detectado. Llamando a processDiscovery con MAC: {}", payload.trim());

                // Lógica que registra el dispositivo

                provisioningService.processDiscovery(payload.trim(), brokerUrl);
                return;
            }

            // 2. Manejo de Tópicos de Datos de Sensores
            String dataTopicPrefix = mqttTopicService.getWildcardSubscriptionTopic().replace("/#", "/");
            if (topic.startsWith(dataTopicPrefix)) {

                // --- LOG 3: VERIFICAR RUTA DE DATOS ---
                log.debug("INGESTION: Tópico de datos detectado. Llamando a processSensorData.");
                processSensorData(topic, payload);
            }

        } catch (Exception e) {
            log.error("Error fatal no controlado en MqttIngestionService al manejar el tópico {}: {}", topic, e.getMessage(), e);
        }
    }
    /**
     * Orquesta el flujo de la "Gota de Dato" para lecturas de sensores.
     */
    private void processSensorData(String topic, String payload) {
        // Extraer el plantId del tópico: espera un formato como "planta/ID/lecturas"
        String[] parts = topic.split("/");
        if (parts.length < 3) {
            log.warn("Formato de tópico de datos inválido, ignorando: {}", topic);
            return;
        }
        String plantId = parts[1]; // PlantId es el MQTT Username

        // 2.1. Buscar el Dispositivo
        Optional<PlantDevice> deviceOpt = deviceRepository.findByPlantId(plantId);
        if (deviceOpt.isEmpty() || !deviceOpt.get().getIsActive()) {
            log.warn("❌ Dato de lectura ignorado. PlantId desconocido o inactivo: {}", plantId);
            return;
        }
        PlantDevice device = deviceOpt.get();

        // 2.2. Aseguramiento de Calidad (QC)
        // El QC Service es responsable de deserializar el JSON y validar
        Reading qcReading = qcLayerService.applyQualityCheck(payload, device);

        // 2.3. Lógica principal: Si el QC pasa, se envía al Advisor y se persiste.
        if (qcReading.getQcStatus() == Reading.QcStatus.VALID) {

            // Ejecutar el Advisor (establece CRITICA, ALERTA, INFO)
            Reading finalReading = advisorService.evaluateReading(qcReading, device);

            // ACTUALIZACIÓN CRÍTICA: Monitoreo de Heartbeat
            device.setLastDataReceived(Instant.now());

            // 2.4. Persistencia: Guardar la lectura y actualizar el estado de Heartbeat del dispositivo
            readingRepository.save(finalReading);
            deviceRepository.save(device); // <-- Persiste el lastDataReceived

            log.info("✅ Lectura de {} válida. Resultado: {}", plantId, finalReading.getAdvisorResult());

        } else {
            // Si falla el QC, se guarda para auditoría pero se marca con el error
            readingRepository.save(qcReading);
            log.warn("⚠️ Lectura de {} descartada por QC. Status: {}", plantId, qcReading.getQcStatus());
        }
    }
}