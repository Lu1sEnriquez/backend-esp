package com.api.plant.repository;

import com.api.plant.entity.MqttBroker;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional; // Importamos Optional

/**
 * Repositorio para la entidad MqttBroker.
 * Incluye métodos para la arquitectura Multi-Broker y el Provisioning.
 */
public interface MqttBrokerRepository extends MongoRepository<MqttBroker, String> {

    /**
     * Busca TODOS los brokers que tienen la bandera isActive en true.
     * (Usado por MqttDiscoveryService).
     */
    List<MqttBroker> findByIsActiveTrue();

    /**
     * Busca un broker por su campo de URL (sin el 'tcp://' o el puerto).
     * (Usado por DeviceProvisioningService para resolver la URL al brokerId).
     * @param url La URL (ej: "0.tcp.ngrok.io")
     * @return Un Optional<MqttBroker>
     */
    Optional<MqttBroker> findByUrl(String url);
}