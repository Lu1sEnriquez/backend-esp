package com.api.plant.dto; // Creamos un nuevo DTO para el mensaje de salida

import java.time.Instant;
import java.util.Date;

/**
 * Record inmutable para definir el contrato del mensaje de alerta/notificación.
 * Este es el JSON exacto que se envía al cliente React vía WebSocket.
 */
public record NotificationMessage(
        String type,        // CRÍTICA, ALERTA
        String plantId,     // ID de la planta que generó la alerta
        Instant timestamp,     // Momento en que ocurrió la lectura
        String title,       // Mensaje principal de la alerta (ej. "URGENTE! Humedad muy baja.")
        String action       // Sugerencia de acción (ej. "¿Desea activar el riego manual?")
) {}