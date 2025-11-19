package com.api.plant.dto.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Record genérico e inmutable (DTO) para CUALQUIER comando enviado al ESP32.
 * El campo 'command' ahora usa el Enum DeviceCommand para la seguridad de tipos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // Omite 'parameters' del JSON si es nulo
public record GenericCommandPayload(

        // CAMBIO CLAVE: Usar el Enum
        DeviceCommand command,

        Map<String, Object> parameters // Un mapa flexible para cualquier parámetro
) {}