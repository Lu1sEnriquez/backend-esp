package com.api.plant.service;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Esta clase no es un Bean de Spring. Es una clase simple que se instancia
 * por cada conexión de broker en MqttDiscoveryService.
 * Su propósito es saber a qué URL de broker pertenece
 * y pasar esa información al MqttIngestionService.
 */
public class CustomMqttCallback implements MqttCallback {

    private final String brokerUrl;


    private final MqttIngestionService ingestionService;

    public CustomMqttCallback(String brokerUrl, MqttIngestionService ingestionService) {
        this.brokerUrl = brokerUrl;
        this.ingestionService = ingestionService;
    }

    @Override
    public void connectionLost(Throwable cause) {
        // La lógica de reconexión sigue en MqttDiscoveryService
        // ingestionService.handleConnectionLost(brokerUrl, cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // ¡Aquí está la magia! Pasa la URL del broker al servicio de ingestión.
        ingestionService.handleMessage(brokerUrl, topic, message);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // ingestionService.handleDeliveryComplete(brokerUrl, token);
    }
}