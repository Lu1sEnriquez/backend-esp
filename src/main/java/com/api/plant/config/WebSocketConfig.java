package com.api.plant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilita un Simple Broker de memoria para manejar las suscripciones a '/topic'
        config.enableSimpleBroker("/topic");
        // Prefijo para los mensajes dirigidos a los controladores de la aplicación
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Divide la cadena de URLs de entorno separada por comas en una lista de Strings
        String[] allowedOriginsArray = frontendUrl.split(",");

        // Configura el endpoint '/ws' para las conexiones STOMP (WebSocket)
        // Usa `setAllowedOriginPatterns` para pasar el arreglo de URLs permitidas
        registry.addEndpoint("/ws")
                // Acepta los patrones de origen definidos en la variable de entorno
                .setAllowedOriginPatterns(allowedOriginsArray)
                // Habilita la capa de fallback de SockJS
                .withSockJS();

        // Opcionalmente, puedes añadir un endpoint sin SockJS si lo prefieres:
        // registry.addEndpoint("/websocket")
        //         .setAllowedOriginPatterns(allowedOriginsArray);
    }
}