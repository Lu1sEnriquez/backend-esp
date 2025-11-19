package com.api.plant.service;

import com.api.plant.dto.NotificationMessage;
import com.api.plant.entity.Reading;
import com.api.plant.entity.Reading.AdvisorResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de enviar notificaciones en tiempo real vía WebSocket.
 */
@Service
public class NotificationService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Envía una alerta al usuario propietario de la planta.
     * @param reading La lectura que disparó la alerta.
     * @param userId El ID del usuario propietario.
     * @param advisorResult El resultado del Advisor (Enum) para el switch.
     */
    public void sendAlert(Reading reading, String userId, AdvisorResult advisorResult) {

        String title;
        String actionSuggestion;

        // Usamos el Enum para la seguridad de tipos
        switch (advisorResult) {
            case CRITICA:
                // CRÍTICA siempre es por Humedad de SUELO BAJA
                title = "🚨 CRÍTICA: ¡Riesgo de sequía! Humedad de suelo en " + reading.getSoilHumidity() + "%.";
                actionSuggestion = "Verifique la planta inmediatamente y active el riego. Revise si hay fallas en la bomba.";
                break;
            case ALERTA:
                // ALERTA cubre Temp. extrema, Humedad ambiental extrema, Humedad de suelo alta o Luz excesiva.
                title = "⚠️ ALERTA: Condiciones ambientales/suelo detectadas.";
                // Proporcionamos un resumen de las métricas clave para que el usuario diagnostique mejor.
                actionSuggestion = String.format(
                        "Métricas Actuales: Temp: %.1f°C, Hum.Amb: %d%%, Hum.Suelo: %d%%. Revise el log para el detalle de la causa.",
                        reading.getTempC(),
                        reading.getAmbientHumidity(),
                        reading.getSoilHumidity()
                );
                break;
            case RECOMENDACION:
                // RECOMENDACION siempre es por Luz baja
                title = "💡 RECOMENDACIÓN: Nivel de luz bajo (" + reading.getLightLux() + " lux).";
                actionSuggestion = "Considere mover la planta a un lugar con mejor iluminación.";
                break;
            case INFO:
            default:
                return; // No enviar notificación para "INFO" o estados desconocidos.
        }

        // 1. CREACIÓN DEL MENSAJE
        NotificationMessage message = new NotificationMessage(
                advisorResult.name(),
                reading.getPlantId(),
                reading.getTimestamp(),
                title,
                actionSuggestion
        );

        // 2. Enviar el Mensaje por WebSocket al Usuario Específico
        // La ruta es: /user/{userId}/queue/alerts
        messagingTemplate.convertAndSendToUser(
                userId,                             // User ID (Destino)
                "/queue/alerts",                    // Tópico privado del usuario
                message                             // Payload del mensaje
        );

        System.out.println(">>> [NOTIFICACIÓN] Alerta " + advisorResult.name() + " enviada al usuario: " + userId);
    }
}