package com.api.plant.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "readings")
public class Reading {

    // --- ENUMS (mantenemos los existentes) ---
    public enum QcStatus {
        VALID, OUT_OF_RANGE, RATE_ERROR, QC_ERROR, EVENT
    }

    public enum AdvisorResult {
        CRITICA, ALERTA, RECOMENDACION, INFO
    }

    public enum MessageType {
        READING, EVENT
    }

    // --- CAMPOS ---
    @Id
    private String id;
    private String plantId;
    private String userId;
    private Instant timestamp = Instant.now();

    private Double tempC;
    private Integer ambientHumidity;
    private Integer lightLux;
    private Integer soilHumidity;

    // 🔥 CAMBIO: Usar Boolean en lugar de enum
    private Boolean pumpOn; // true = ON, false = OFF, null = no info

    private MessageType msgType = MessageType.READING;
    private QcStatus qcStatus = QcStatus.QC_ERROR;
    private AdvisorResult advisorResult = AdvisorResult.INFO;

    // --- CONSTRUCTORES ---
    public Reading() {}

    // Constructor para eventos de bomba
    public static Reading createPumpEvent(String plantId, boolean pumpOn) {
        Reading reading = new Reading();
        reading.setPlantId(plantId);
        reading.setPumpOn(pumpOn);
        reading.setMsgType(MessageType.EVENT);
        reading.setQcStatus(QcStatus.EVENT);
        return reading;
    }

    // --- GETTERS Y SETTERS ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlantId() { return plantId; }
    public void setPlantId(String plantId) { this.plantId = plantId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Double getTempC() { return tempC; }
    public void setTempC(Double tempC) { this.tempC = tempC; }

    public Integer getAmbientHumidity() { return ambientHumidity; }
    public void setAmbientHumidity(Integer ambientHumidity) { this.ambientHumidity = ambientHumidity; }

    public Integer getLightLux() { return lightLux; }
    public void setLightLux(Integer lightLux) { this.lightLux = lightLux; }

    public Integer getSoilHumidity() { return soilHumidity; }
    public void setSoilHumidity(Integer soilHumidity) { this.soilHumidity = soilHumidity; }

    // 🔥 CAMBIO: Getters/Setters con Boolean
    public Boolean getPumpOn() { return pumpOn; }
    public void setPumpOn(Boolean pumpOn) { this.pumpOn = pumpOn; }

    public MessageType getMsgType() { return msgType; }
    public void setMsgType(MessageType msgType) { this.msgType = msgType; }

    public QcStatus getQcStatus() { return qcStatus; }
    public void setQcStatus(QcStatus qcStatus) { this.qcStatus = qcStatus; }

    public AdvisorResult getAdvisorResult() { return advisorResult; }
    public void setAdvisorResult(AdvisorResult advisorResult) { this.advisorResult = advisorResult; }

    // 🔥 MÉTODO CONVENIENTE
    public boolean isPumpOn() {
        return Boolean.TRUE.equals(pumpOn);
    }
}