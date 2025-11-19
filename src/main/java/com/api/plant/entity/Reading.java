package com.api.plant.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Entidad que representa una lectura de sensor persistida en Mongo Atlas.
 * Incluye los resultados del QC y del Advisor.
 */
@Document(collection = "readings")
public class Reading {

    // --- 1. ENUMS (Resultados de Lógica) ---

    /**
     * Enum que define el estado de la lectura después de la validación de Calidad (QC).
     */
    public enum QcStatus {
        VALID,              // Dato validado y apto para el Advisor.
        OUT_OF_RANGE,       // Descartado por límites físicos (ej. Temp > 50C).
        RATE_ERROR,         // Descartado por salto brusco (Outlier Lógico).
        QC_ERROR            // Error de formato o deserialización.
    }

    /**
     * Enum que define el resultado de la Lógica Central (Advisor).
     */
    public enum AdvisorResult {
        CRITICA,            // Humedad baja de SUELO -> Requiere RIEGO y Alerta.
        ALERTA,             // Temp/Humedad AMBIENTAL alta/baja -> Requiere Notificación.
        RECOMENDACION,      // Luz baja/Problema leve -> Sugerencia para el feed.
        INFO                // Todo normal.
    }

    // --- 2. CAMPOS DE DATOS ---

    @Id
    private String id;

    private String plantId;      // ID del dispositivo/planta (MQTT Username)
    private String userId;       // ID del usuario dueño (para segmentación de lecturas)

    private Instant timestamp = Instant.now();

    // DHT11 - Payload: temp_c
    private Double tempC;

    // DHT11 - Payload: humidity_p -> Humedad Ambiental
    private Integer ambientHumidity; // Renombrado

    // BH1750 - Payload: light_lux
    private Integer lightLux;

    // Sensor de Suelo - Payload: soil_humidity -> Humedad de Suelo
    private Integer soilHumidity; // Renombrado

    // --- 3. CAMPOS DE RESULTADO DEL PROCESAMIENTO ---

    private QcStatus qcStatus = QcStatus.QC_ERROR;
    private AdvisorResult advisorResult = AdvisorResult.INFO;

    // Constructor vacío
    public Reading() {
    }

    // --- 4. GETTERS Y SETTERS ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlantId() {
        return plantId;
    }

    public void setPlantId(String plantId) {
        this.plantId = plantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getTempC() {
        return tempC;
    }

    public void setTempC(Double tempC) {
        this.tempC = tempC;
    }

    // Humedad AMBIENTAL
    public Integer getAmbientHumidity() {
        return ambientHumidity;
    }

    public void setAmbientHumidity(Integer ambientHumidity) {
        this.ambientHumidity = ambientHumidity;
    }

    public Integer getLightLux() {
        return lightLux;
    }

    public void setLightLux(Integer lightLux) {
        this.lightLux = lightLux;
    }

    // Humedad de SUELO
    public Integer getSoilHumidity() {
        return soilHumidity;
    }

    public void setSoilHumidity(Integer soilHumidity) {
        this.soilHumidity = soilHumidity;
    }

    public QcStatus getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(QcStatus qcStatus) {
        this.qcStatus = qcStatus;
    }

    public AdvisorResult getAdvisorResult() {
        return advisorResult;
    }

    public void setAdvisorResult(AdvisorResult advisorResult) {
        this.advisorResult = advisorResult;
    }
}