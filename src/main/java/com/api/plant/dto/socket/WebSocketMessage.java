// WebSocketMessage.java
package com.api.plant.dto.socket;

import com.api.plant.entity.Reading;

import java.time.Instant;

public class WebSocketMessage<T> {
    private final String type;
    private final String plantId;
    private final Instant timestamp;
    private final T data;

    // Constructor principal
    public WebSocketMessage(String type, String plantId, T data) {
        this.type = type;
        this.plantId = plantId;
        this.timestamp = Instant.now();
        this.data = data;
    }

    // Factory methods para crear mensajes fácilmente
    public static WebSocketMessage<TelemetryData> createTelemetry(String plantId, Reading reading) {
        return new WebSocketMessage<>("TELEMETRY", plantId, new TelemetryData(reading));
    }

    public static WebSocketMessage<PumpEvent> createPumpEvent(String plantId, Reading reading) {
        return new WebSocketMessage<>("PUMP_EVENT", plantId, new PumpEvent(reading));
    }

    public static WebSocketMessage<Alert> createAlert(String plantId, Reading reading) {
        return new WebSocketMessage<>("ALERT", plantId, new Alert(reading));
    }

    // Getters
    public String getType() { return type; }
    public String getPlantId() { return plantId; }
    public Instant getTimestamp() { return timestamp; }
    public T getData() { return data; }
}