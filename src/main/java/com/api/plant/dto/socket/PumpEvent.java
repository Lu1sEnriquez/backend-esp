package com.api.plant.dto.socket;

import com.api.plant.entity.Reading;

public record PumpEvent(
        Boolean pumpOn,
        String event
) {
    public PumpEvent(Reading reading) {
        this(
                reading.isPumpOn(),
                reading.isPumpOn() ? "PUMP_ON" : "PUMP_OFF"
        );
    }
}