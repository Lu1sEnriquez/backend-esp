package com.api.plant.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

/**
 * Entidad que mapea un dispositivo físico (ESP32) a un usuario y a la lógica Advisor.
 */
@Document(collection = "plant_devices")
public class PlantDevice {

    @Id
    private String id;
    private String ownerId;
    private String plantId;
    private String name;
    private String description;

    // --- Campo Crucial para Provisioning ---
    @Indexed(unique = true, sparse = true)
    private String macAddress; // ID físico de la placa (usado antes de la vinculación)
    // ---------------------------------------

    // =====================================
    // Umbrales de Lógica del Advisor
    // =====================================

    // Umbrales de HUMEDAD AMBIENTAL (DHT11 - para ALERTA)
    private Integer minHumidity; // Mínimo de humedad ambiental
    private Integer maxHumidity; // Máximo de humedad ambiental (para sugerir ALERTA)

    // 🆕 Umbrales de HUMEDAD de SUELO (Sensor de suelo - para RIEGO CRÍTICO)
    // CRÍTICA: SoilHumidity < minSoilHumidity -> Activar Riego
    private Integer minSoilHumidity;
    private Integer maxSoilHumidity; // Puede usarse para detectar inundación

    // Umbrales de TEMPERATURA (DHT11 - para ALERTA)
    private Double minTempC;      // Mínimo de temperatura para sugerir ALERTA (Riesgo de frío)
    private Double maxTempC;      // Máximo de temperatura para sugerir ALERTA (Riesgo de calor)

    // Umbrales de LUZ (BH1750 - para RECOMENDACIÓN/ALERTA)
    private Integer minLightLux;  // Mínimo de luz para sugerir RECOMENDACIÓN [cite: 147]
    private Integer maxLightLux;  // Máximo de luz para sugerir ALERTA (Riesgo de quemadura)

    // Campos de Operación
    private String topic;
    private Boolean isActive;
    private Instant lastDataReceived; // Para Heartbeat/Monitoreo
    private Integer qosLevel = 1;

    public PlantDevice() {
    }

    // =====================================
    // Getters y Setters (Originales)
    // =====================================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }



    public String getPlantId() {
        return plantId;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }



    // --- Humedad AMBIENTAL ---
    public Integer getMinHumidity() {
        return minHumidity;
    }

    public void setMinHumidity(Integer minHumidity) {
        this.minHumidity = minHumidity;
    }

    public Integer getMaxHumidity() {
        return maxHumidity;
    }

    public void setMaxHumidity(Integer maxHumidity) {
        this.maxHumidity = maxHumidity;
    }

    // --- Temperatura ---
    public Double getMinTempC() {
        return minTempC;
    }

    public void setMinTempC(Double minTempC) {
        this.minTempC = minTempC;
    }

    public Double getMaxTempC() {
        return maxTempC;
    }

    public void setMaxTempC(Double maxTempC) {
        this.maxTempC = maxTempC;
    }

    // --- Luz ---
    public Integer getMinLightLux() {
        return minLightLux;
    }

    public void setMinLightLux(Integer minLightLux) {
        this.minLightLux = minLightLux;
    }

    public Integer getMaxLightLux() {
        return maxLightLux;
    }

    public void setMaxLightLux(Integer maxLightLux) {
        this.maxLightLux = maxLightLux;
    }

    // =====================================
    // 🆕 Getters y Setters (Humedad de Suelo)
    // =====================================

    public Integer getMinSoilHumidity() {
        return minSoilHumidity;
    }

    public void setMinSoilHumidity(Integer minSoilHumidity) {
        this.minSoilHumidity = minSoilHumidity;
    }

    public Integer getMaxSoilHumidity() {
        return maxSoilHumidity;
    }

    public void setMaxSoilHumidity(Integer maxSoilHumidity) {
        this.maxSoilHumidity = maxSoilHumidity;
    }

    // =====================================
    // Getters y Setters (Operación)
    // =====================================

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Instant getLastDataReceived() {
        return lastDataReceived;
    }

    public void setLastDataReceived(Instant lastDataReceived) {
        this.lastDataReceived = lastDataReceived;
    }

    public Integer getQosLevel() {
        return qosLevel;
    }

    public void setQosLevel(Integer qosLevel) {
        this.qosLevel = qosLevel;
    }
}