package com.api.plant.service;

import com.api.plant.dto.device.PlantDeviceUpdateDto;
import com.api.plant.entity.AppUser;
import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.AppUserRepository;
import com.api.plant.repository.MqttBrokerRepository;
import com.api.plant.repository.PlantDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceService {

    @Autowired
    private PlantDeviceRepository plantDeviceRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private MqttTopicService mqttTopicService;

    @Autowired
    private DeviceProvisioningService provisioningService;

    @Autowired
    private MqttBrokerRepository brokerRepository;


    // --- INYECCIÓN DE TODOS LOS UMBRALES POR DEFECTO ---
    @Value("${device.thresholds.humidity.min:30}")
    private Integer defaultMinHumidity;

    @Value("${device.thresholds.humidity.max:100}")
    private Integer defaultMaxHumidity; // NUEVO

    @Value("${device.thresholds.humiditySoil.min:30}")
    private Integer defaultMinSoilHumidity;

    @Value("${device.thresholds.humiditySoil.max:100}")
    private Integer defaultMaxSoilHumidity; // NUEVO


    @Value("${device.thresholds.temperature.min:-10.0}")
    private Double defaultMinTempC; // NUEVO

    @Value("${device.thresholds.temperature.max:35.0}")
    private Double defaultMaxTempC; // Renombrado a defaultMaxTempC para consistencia

    @Value("${device.thresholds.light.min:150}")
    private Integer defaultMinLightLux; // NUEVO

    @Value("${device.thresholds.light.max:10000}")
    private Integer defaultMaxLightLux; // NUEVO
    // ---------------------------------------------------


    /**
     * Genera una contraseña segura para MQTT.
     */
    private String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Helper para obtener el Broker ID activo (Usado solo para registro manual).
     */
    private String getActiveBrokerId() {
        return brokerRepository.findByIsActiveTrue().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay broker activo para asignar al dispositivo."))
                .getId();
    }


    /**
     * [Legacy/Manual] Registra un nuevo dispositivo directamente (sin usar el flujo de Provisioning por MAC).
     */
    public PlantDevice registerNewDevice(String userId,String name, String description) throws Exception {

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("Usuario no encontrado."));

        // 1. Generar Credenciales Únicas
        String plantId = "PNT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String mqttPassword = generateSecurePassword();
        String topic = mqttTopicService.getDeviceDataTopic(plantId);

        // 2. Crear la Entidad PlantDevice
        PlantDevice newDevice = new PlantDevice();
        newDevice.setOwnerId(userId);
        newDevice.setPlantId(plantId);
        newDevice.setMqttPassword(mqttPassword);
        newDevice.setName(name);
        newDevice.setDescription(description != null ? description : "Nueva Planta");
        newDevice.setTopic(topic);
        newDevice.setIsActive(true);
        newDevice.setBrokerId(getActiveBrokerId()); // Asignar el broker activo

        // 3. ASIGNAR TODOS LOS UMBRALES POR DEFECTO
        newDevice.setMinHumidity(defaultMinHumidity);
        newDevice.setMaxHumidity(defaultMaxHumidity);
        newDevice.setMinSoilHumidity(defaultMinSoilHumidity);
        newDevice.setMaxSoilHumidity(defaultMaxSoilHumidity);
        newDevice.setMinTempC(defaultMinTempC);
        newDevice.setMaxTempC(defaultMaxTempC);
        newDevice.setMinLightLux(defaultMinLightLux);
        newDevice.setMaxLightLux(defaultMaxLightLux);

        PlantDevice savedDevice = plantDeviceRepository.save(newDevice);

        // 4. Actualizar el AppUser
        user.getPlantsIds().add(plantId);
        userRepository.save(user);

        return savedDevice;
    }


    @Transactional(rollbackFor = Exception.class)
    public PlantDevice syncDeviceToUser(String userId, String macAddress, String name, String description) throws Exception {

        // 1. Buscar el dispositivo descubierto por su MAC
        PlantDevice device = plantDeviceRepository.findByMacAddress(macAddress)
                .orElseThrow(() -> new IllegalStateException("Dispositivo no encontrado o no disponible para sincronizar."));

        // 2. GENERAR CREDENCIALES ÚNICAS
        String plantId = "PNT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String mqttPassword = generateSecurePassword();
        String topic = mqttTopicService.getDeviceDataTopic(plantId);

        // 3. ACTUALIZAR ESTADO DEL DISPOSITIVO
        device.setOwnerId(userId);
        device.setPlantId(plantId);
        device.setMqttPassword(mqttPassword);
        device.setName(name);
        device.setDescription(description);
        device.setTopic(topic);
        device.setIsActive(true);
        // device.setBrokerId ya fue asignado en processDiscovery

        // 4. ASIGNAR TODOS LOS UMBRALES POR DEFECTO
        device.setMinHumidity(defaultMinHumidity);
        device.setMaxHumidity(defaultMaxHumidity);
        device.setMinSoilHumidity(defaultMinSoilHumidity);
        device.setMaxSoilHumidity(defaultMaxSoilHumidity);
        device.setMinTempC(defaultMinTempC);
        device.setMaxTempC(defaultMaxTempC);
        device.setMinLightLux(defaultMinLightLux);
        device.setMaxLightLux(defaultMaxLightLux);

        // 5. PERSISTIR
        PlantDevice savedDevice = plantDeviceRepository.save(device);

        // 6. DISPARAR PROVISIONING REMOTO
        provisioningService.sendConfigurationCommand(savedDevice);

        return savedDevice;
    }

    /**
     * Obtiene la lista de dispositivos de un propietario.
     */
    public List<PlantDevice> getDevicesByOwner(String userId) {
        return plantDeviceRepository.findByOwnerId(userId);
    }

    /**
     * Verifica si el usuario (ownerId) es el dueño del dispositivo (plantId).
     */
    public boolean isUserOwnerOfPlant(String userId, String plantId) {
        Optional<PlantDevice> deviceOpt = plantDeviceRepository.findByPlantId(plantId);
        return deviceOpt.isPresent() && deviceOpt.get().getOwnerId().equals(userId);
    }

    /**
     * Obtiene un dispositivo por su PlantId.
     */
    public Optional<PlantDevice> getDeviceByPlantId(String plantId) {
        return plantDeviceRepository.findByPlantId(plantId);
    }

    /**
     * Busca en la base de datos todos los dispositivos que han sido descubiertos
     * (tienen una MAC) pero que aún no han sido asignados a un usuario (ownerId es nulo).
     */
    public List<PlantDevice> findAvailableDevices() {
        return plantDeviceRepository.findByOwnerIdIsNullAndMacAddressIsNotNull();
    }


    /**
     * Actualiza los umbrales de una planta existente.
     * Aplica solo los campos que se envían en el DTO (que no son null).
     */
    public PlantDevice updateThresholds(String plantId, PlantDeviceUpdateDto updateDto) throws Exception {
        PlantDevice device = plantDeviceRepository.findByPlantId(plantId)
                .orElseThrow(() -> new Exception("PlantDevice no encontrado para actualización."));

        // Aplicar solo los cambios proporcionados por el DTO

        // HUMEDAD hambiental
        if (updateDto.minHumidity() != null) {
            device.setMinHumidity(updateDto.minHumidity());
        }
        if (updateDto.maxHumidity() != null) {
            device.setMaxHumidity(updateDto.maxHumidity());
        }

        //Humedad suelo
        if (updateDto.minSoilHumidity() != null) {
            device.setMinSoilHumidity(updateDto.minSoilHumidity());
        }
        if (updateDto.maxSoilHumidity() != null) {
            device.setMaxSoilHumidity(updateDto.maxSoilHumidity());
        }

        // TEMPERATURA
        if (updateDto.minTempC() != null) {
            device.setMinTempC(updateDto.minTempC());
        }
        if (updateDto.maxTempC() != null) {
            device.setMaxTempC(updateDto.maxTempC());
        }

        // LUZ
        if (updateDto.minLightLux() != null) {
            device.setMinLightLux(updateDto.minLightLux());
        }
        if (updateDto.maxLightLux() != null) {
            device.setMaxLightLux(updateDto.maxLightLux());
        }

        return plantDeviceRepository.save(device);
    }
}