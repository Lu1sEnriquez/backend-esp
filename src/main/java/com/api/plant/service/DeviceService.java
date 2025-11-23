package com.api.plant.service;

import com.api.plant.dto.device.PlantDeviceUpdateDto;
import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.PlantDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    @Autowired
    private PlantDeviceRepository plantDeviceRepository;

    @Autowired
    private MqttTopicService mqttTopicService;

    // --- UMBRALES POR DEFECTO (Desde properties) ---
    @Value("${device.thresholds.humidity.min:30}")
    private Integer defaultMinHumidity;
    @Value("${device.thresholds.humidity.max:100}")
    private Integer defaultMaxHumidity;
    @Value("${device.thresholds.humiditySoil.min:35}")
    private Integer defaultMinSoilHumidity;
    @Value("${device.thresholds.humiditySoil.max:100}")
    private Integer defaultMaxSoilHumidity;
    @Value("${device.thresholds.temperature.min:-10.0}")
    private Double defaultMinTempC;
    @Value("${device.thresholds.temperature.max:38.0}")
    private Double defaultMaxTempC;
    @Value("${device.thresholds.light.min:200}")
    private Integer defaultMinLightLux;
    @Value("${device.thresholds.light.max:50000}")
    private Integer defaultMaxLightLux;

    // ... (getDevicesByOwner y getDeviceByPlantId se mantienen igual) ...
    public List<PlantDevice> getDevicesByOwner(String userId) {
        // Ahora sí podríamos filtrar por dueño si quisieras:
        // return plantDeviceRepository.findByOwnerId(userId);
        return plantDeviceRepository.findByIsActiveTrue();
    }

    public Optional<PlantDevice> getDeviceByPlantId(String plantId) {
        return plantDeviceRepository.findByPlantId(plantId);
    }

    /**
     * Crea una planta asignándole dueño y configuración por defecto.
     */
    public PlantDevice createDevice(String plantId, String name, String userId) {
        if (plantDeviceRepository.findByPlantId(plantId).isPresent()) {
            throw new RuntimeException("¡Esta planta ya existe!");
        }

        PlantDevice device = new PlantDevice();
        device.setPlantId(plantId);
        device.setName(name);

        // 1. Asignar Dueño
        device.setOwnerId(userId); // <--- Aquí asignamos el ID del usuario que llega del front

        device.setIsActive(true);
        device.setTopic(mqttTopicService.getDeviceDataTopic(plantId));

        // 2. Asignar Umbrales (Usando los valores inyectados)
        device.setMinHumidity(defaultMinHumidity);
        device.setMaxHumidity(defaultMaxHumidity);
        device.setMinSoilHumidity(defaultMinSoilHumidity);
        device.setMaxSoilHumidity(defaultMaxSoilHumidity);
        device.setMinTempC(defaultMinTempC);
        device.setMaxTempC(defaultMaxTempC);
        device.setMinLightLux(defaultMinLightLux);
        device.setMaxLightLux(defaultMaxLightLux);

        device.setLastDataReceived(Instant.now());

        return plantDeviceRepository.save(device);
    }


    public PlantDevice updateThresholds(String plantId, PlantDeviceUpdateDto updateDto) throws Exception {
        PlantDevice device = plantDeviceRepository.findByPlantId(plantId)
                .orElseThrow(() -> new Exception("PlantDevice no encontrado."));

        if (updateDto.minHumidity() != null) device.setMinHumidity(updateDto.minHumidity());
        if (updateDto.maxHumidity() != null) device.setMaxHumidity(updateDto.maxHumidity());
        if (updateDto.minSoilHumidity() != null) device.setMinSoilHumidity(updateDto.minSoilHumidity());
        if (updateDto.maxSoilHumidity() != null) device.setMaxSoilHumidity(updateDto.maxSoilHumidity());
        if (updateDto.minTempC() != null) device.setMinTempC(updateDto.minTempC());
        if (updateDto.maxTempC() != null) device.setMaxTempC(updateDto.maxTempC());
        if (updateDto.minLightLux() != null) device.setMinLightLux(updateDto.minLightLux());
        if (updateDto.maxLightLux() != null) device.setMaxLightLux(updateDto.maxLightLux());

        return plantDeviceRepository.save(device);
    }
}