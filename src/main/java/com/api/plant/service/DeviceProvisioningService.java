    package com.api.plant.service;

    import com.api.plant.dto.command.DeviceCommand;
    import com.api.plant.dto.command.GenericCommandPayload;
    import com.api.plant.entity.MqttBroker;
    import com.api.plant.entity.PlantDevice;
    import com.api.plant.repository.MqttBrokerRepository;
    import com.api.plant.repository.PlantDeviceRepository;
    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import org.eclipse.paho.client.mqttv3.MqttClient;
    import org.eclipse.paho.client.mqttv3.MqttMessage;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.context.annotation.Lazy;
    import org.springframework.stereotype.Service;

    import java.nio.charset.StandardCharsets;
    import java.util.Map;
    import java.util.Optional;

    @Service
    public class DeviceProvisioningService {

        private static final Logger log = LoggerFactory.getLogger(DeviceProvisioningService.class);

        // --- Dependencias Finales (Inyectadas por Constructor) ---
        private final PlantDeviceRepository plantDeviceRepository;
        private final MqttTopicService mqttTopicService;
        private final MqttDiscoveryService mqttDiscoveryService;
        private final ObjectMapper objectMapper;
        private final MqttBrokerRepository brokerRepository; // <-- El repo que faltaba

        // --- Constructor Único (Inyección Limpia) ---
        public DeviceProvisioningService(PlantDeviceRepository plantDeviceRepository,
                                         MqttTopicService mqttTopicService,
                                         @Lazy MqttDiscoveryService mqttDiscoveryService,
                                         ObjectMapper objectMapper,
                                         MqttBrokerRepository brokerRepository) { // <-- AÑADIDO
            this.plantDeviceRepository = plantDeviceRepository;
            this.mqttTopicService = mqttTopicService;
            this.mqttDiscoveryService = mqttDiscoveryService;
            this.objectMapper = objectMapper;
            this.brokerRepository = brokerRepository; // <-- AÑADIDO
        }

        /**
         * FUNCIÓN 1: Procesa el anuncio de un nuevo dispositivo (MAC Address).
         * @param macAddress La MAC del hardware.
         * @param brokerUrl La URL del broker que descubrió este dispositivo.
         */
        /**
         * FUNCIÓN 1: Procesa el anuncio de un nuevo dispositivo (MAC Address).
         * @param macAddress La MAC del hardware.
         * @param brokerUrl La URL del broker que descubrió este dispositivo.
         */
        public void processDiscovery(String macAddress, String brokerUrl) {

            // 1. LOG DE ENTRADA
            log.info("PROVISIONING: MAC : [{}].", macAddress);


            // --- LOG 1: VALIDAR MAC ---
            if (macAddress == null || macAddress.length() < 12) {
                log.warn("PROVISIONING: MAC Address recibida es inválida o vacía: [{}]. Descartando.", macAddress);
                return;
            }
            log.debug("PROVISIONING: Buscando dispositivo por MAC: {}", macAddress);

            Optional<PlantDevice> deviceOpt = plantDeviceRepository.findByMacAddress(macAddress);

            // --- LOG 2: VERIFICAR SI YA EXISTE ---
            if (deviceOpt.isPresent()) {
                log.info("PROVISIONING: MAC {} ya está registrada en la DB. Ignorando anuncio de descubrimiento.", macAddress);
                return; // No es un error, es normal.
            }

            // --- Dispositivo es Nuevo ---
            log.info("PROVISIONING: MAC {} es nueva. Intentando resolver Broker URL: {}", macAddress, brokerUrl);

            // ¡CAMBIO CLAVE! Buscar el ID del Broker usando la URL
            String urlKey = brokerUrl.replace("tcp://", "").split(":")[0];
            log.debug("PROVISIONING: Buscando BrokerId usando la URL key: {}", urlKey);

            String brokerId = brokerRepository.findByUrl(urlKey).map(MqttBroker::getId).orElse(null);

            if (brokerId == null) {
                // Si la base de datos no tiene un documento con url: "localhost", el registro falla.
                log.error("PROVISIONING: ¡FALLO! No se encontró ningún Broker en la DB con la URL [{}].", urlKey);
                return; // <--- EL REGISTRO SE DETIENE AQUÍ
            }

            log.debug("PROVISIONING: BrokerId {} encontrado. Creando nuevo PlantDevice...", brokerId);

            PlantDevice newDevice = new PlantDevice();
            newDevice.setPlantId("TEMP-" + macAddress.substring(macAddress.length() - 4));
            newDevice.setName("Dispositivo Desconocido");
            newDevice.setMacAddress(macAddress);
            newDevice.setBrokerId(brokerId); // ¡GUARDAMOS EL ID DEL BROKER CORRECTO!
            newDevice.setIsActive(false); // Inactivo hasta que el usuario lo sincronice

            try {
                plantDeviceRepository.save(newDevice);
                // --- LOG 4: ÉXITO ---
                log.info("📡 ¡Nuevo dispositivo descubierto y guardado en DB! MAC: {}. En Broker: {}", macAddress, brokerId);
            } catch (Exception e) {
                // --- LOG 5: FALLO DE BASE DE DATOS ---
                log.error("PROVISIONING: ¡FALLO DE DB! No se pudo guardar el nuevo dispositivo para MAC {}: {}", macAddress, e.getMessage());
            }
        }


        /**
         * FUNCIÓN 2 (CORE): Envía el comando CONFIG_SET con las credenciales MQTT únicas al ESP32.
         * Este método se llama desde un Controller/Service después de que un usuario vincula un dispositivo.
         * @param device El dispositivo PlantDevice ya actualizado con el ownerId, plantId y mqttPassword únicos.
         */
        public void sendConfigurationCommand(PlantDevice device) throws Exception {

            // 1. Obtener el cliente activo (Asumimos que el broker de provisioning está activo)
            // Usamos un metodo del Discovery Service o ActuatorService para obtener un cliente activo
            MqttClient client = mqttDiscoveryService.getAnyActiveMqttClient()
                    .orElseThrow(() -> new IllegalStateException("No hay clientes MQTT activos para el provisioning."));

            // 2. Tópico de Control: Usa la MAC para el tópico de escucha específico del ESP32
            String topic = mqttTopicService.getProvisioningConfigTopic(device.getMacAddress());

            // 3. Payload: Contiene las credenciales que el ESP32 debe guardar (persistencia local)
            Map<String, Object> parameters = Map.of(
                    "new_user", device.getPlantId(),        // El nuevo MQTT Username
                    "new_pass", device.getMqttPassword(),   // La nueva MQTT Password
                    "new_topic", device.getTopic()          // Tópico donde debe publicar sus datos
            );

            GenericCommandPayload payloadObject = new GenericCommandPayload(
                    DeviceCommand.CONFIG_SET,
                    parameters
            );

            // 4. Serializar y publicar
            publishCommand(client, topic, payloadObject);

            log.warn("🔗 Credenciales únicas enviadas a MAC {}. Esperando reconexión...", device.getMacAddress());
        }

        // --- Lógica de Publicación Auxiliar (Reutilizada del ActuatorService) ---
        // En DeviceProvisioningService.java

    // ... (Metodo sendConfigurationCommand se mantiene igual) ...

        // --- Lógica de Publicación Auxiliar (CORREGIDA) ---
        private void publishCommand(MqttClient client, String topic, GenericCommandPayload payloadObject) throws Exception {
            String payload;
            try {
                payload = objectMapper.writeValueAsString(payloadObject);
            } catch (JsonProcessingException e) {
                log.error("❌ Error al serializar el payload del comando {}.", payloadObject.command().name(), e);
                throw new RuntimeException("Error de serialización.", e);
            }

            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(1);

            // --- ¡CORRECCIÓN CRÍTICA! ---
            // El comando CONFIG_SET DEBE ser RETAINED para que el ESP32 lo reciba
            // inmediatamente después de suscribirse, incluso si el comando fue publicado antes.
            if (payloadObject.command() == DeviceCommand.CONFIG_SET) {
                message.setRetained(true);
                log.warn("PROVISIONING: CONFIG_SET publicado como RETAINED para entrega garantizada.");
            }
            // -------------------------

            client.publish(topic, message);
            log.info("Comando {} publicado. Tópico: {}, Payload: {}", payloadObject.command().name(), topic, payload);
        }
    }