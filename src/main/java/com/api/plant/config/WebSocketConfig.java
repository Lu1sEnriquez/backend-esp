package com.api.plant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configura el servidor de WebSocket.
 */
@Configuration
@EnableWebSocketMessageBroker // Habilita el procesamiento de mensajes de WebSocket respaldado por un broker de mensajes.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Define el endpoint HTTP que los clientes usarán para conectarse al servidor de WebSocket.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // El frontend se conectará a /ws/notificaciones.
        // .withSockJS() es útil para compatibilidad con navegadores antiguos (fallback).
        registry.addEndpoint("/ws/notificaciones").setAllowedOriginPatterns("*").withSockJS();
    }

    /**
     * Configura el broker de mensajes.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // El prefijo /app es para los mensajes dirigidos a los Controllers de Spring (del cliente al servidor).
        registry.setApplicationDestinationPrefixes("/app");

        // El prefijo /topic y /user es para el broker (del servidor al cliente/usuario).
        // Usaremos /user para enviar notificaciones directas a usuarios específicos.
        registry.enableSimpleBroker("/topic", "/user");

        // El prefijo /user/ es crucial para enviar mensajes privados.
        registry.setUserDestinationPrefix("/user");
    }
}