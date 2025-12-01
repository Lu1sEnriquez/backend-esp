package com.api.plant.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "plant_alerts")
public class PlantAlert {
    @Id
    private String id;
    private String plantId;
    private String severity; // "CRITICA", "ALERTA", "INFO"
    private String message;
    private String metric;   // "SOIL_HUMIDITY", "TEMPERATURE"
    private double value;    // El valor que detonó la alerta (ej. 28.5)
    private Instant timestamp;
    private boolean isRead;  // Para marcar como leído en el frontend

    public PlantAlert() {}

    public PlantAlert(String plantId, String severity, String message, String metric, double value) {
        this.plantId = plantId;
        this.severity = severity;
        this.message = message;
        this.metric = metric;
        this.value = value;
        this.timestamp = Instant.now();
        this.isRead = false;
    }

    // Getters y Setters
    public String getId() { return id; }
    public String getPlantId() { return plantId; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    // ... otros getters
}