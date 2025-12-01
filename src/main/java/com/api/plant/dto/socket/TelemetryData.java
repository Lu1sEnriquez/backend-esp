// Records para datos específicos - son inmutables y concisos
package com.api.plant.dto.socket;

import com.api.plant.entity.Reading;

public record TelemetryData(
        Double temp,
        Integer ambientHum,
        Integer soilHum,
        Integer light,
        Boolean pumpOn,
        String alertLevel
) {
    public TelemetryData(Reading reading) {
        this(
                reading.getTempC(),
                reading.getAmbientHumidity(),
                reading.getSoilHumidity(),
                reading.getLightLux(),
                reading.isPumpOn(),
                reading.getAdvisorResult() != null ? reading.getAdvisorResult().name() : null
        );
    }
}


