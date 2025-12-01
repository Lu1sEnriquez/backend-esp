package com.api.plant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Endpoint para recibir mensajes de prueba del frontend
     * y reenviarlos al tópico correspondiente
     */
    @MessageMapping("/test/{plantId}")
    public void handleTestMessage(@Payload Map<String, Object> message,
                                  @DestinationVariable String plantId) {
        System.out.println("📨 Mensaje de prueba recibido para " + plantId + ": " + message);

        // Reenviar el mensaje al tópico de la planta
        String destination = "/topic/plant/" + plantId;
        messagingTemplate.convertAndSend(destination, message);

        System.out.println("📤 Mensaje reenviado a: " + destination);
    }

    /**
     * Endpoint HTTP para testing básico
     */
    @PostMapping("/send-message")
    public String sendTestMessage(@RequestBody Map<String, Object> request) {
        String plantId = (String) request.get("plantId");
        String type = (String) request.get("type");

        Map<String, Object> testMessage = Map.of(
                "type", type,
                "plantId", plantId,
                "timestamp", java.time.Instant.now().toString(),
                "data", Map.of(
                        "message", "Este es un mensaje de prueba del backend",
                        "randomValue", Math.random() * 100
                )
        );

        messagingTemplate.convertAndSend("/topic/plant/" + plantId, testMessage);
        return "Mensaje de prueba enviado a " + plantId;
    }
}