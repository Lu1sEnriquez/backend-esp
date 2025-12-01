package com.api.plant.dto.socket;


import com.api.plant.entity.Reading;

public record Alert(
        String level,
        String message,
        String metric
) {
    public Alert(Reading reading) {
        this(
                reading.getAdvisorResult().name(),
                getAlertMessage(reading),
                getAlertMetric(reading)
        );
    }

    private static String getAlertMessage(Reading reading) {
        if (reading.getAdvisorResult() == Reading.AdvisorResult.CRITICA) {
            return "Humedad del suelo crítica: " + reading.getSoilHumidity() + "% - Activando riego automático";
        } else if (reading.getAdvisorResult() == Reading.AdvisorResult.ALERTA) {
            return "Temperatura alta: " + reading.getTempC() + "°C";
        }
        return "Condición normal";
    }

    private static String getAlertMetric(Reading reading) {
        if (reading.getSoilHumidity() != null && reading.getAdvisorResult() == Reading.AdvisorResult.CRITICA) {
            return "SOIL_HUMIDITY";
        } else if (reading.getTempC() != null && reading.getAdvisorResult() == Reading.AdvisorResult.ALERTA) {
            return "TEMPERATURE";
        }
        return "INFO";
    }
}