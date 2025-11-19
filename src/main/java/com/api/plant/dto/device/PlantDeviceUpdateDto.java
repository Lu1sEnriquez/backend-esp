package com.api.plant.dto.device;

import java.lang.Double; // Usamos java.lang.Double para ser anulable
import java.lang.Integer; // Usamos java.lang.Integer para ser anulable

/**
 * DTO para la actualización parcial de umbrales del PlantDevice.
 * Todos los campos son opcionales (pueden ser nulos) si no se envían.
 */
public record PlantDeviceUpdateDto(
        // Humedad Hambiental
        Integer minHumidity,
        Integer maxHumidity,

        // Humedad Terreno
        Integer minSoilHumidity,
        Integer maxSoilHumidity,
        // Temperatura
        Double minTempC,
        Double maxTempC,

        // Luz
        Integer minLightLux,
        Integer maxLightLux
) {}