package com.api.plant.service;

import com.api.plant.entity.MqttBroker;
import com.api.plant.repository.MqttBrokerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements CommandLineRunner {

    private final MqttBrokerRepository brokerRepository;

    public DataLoader(MqttBrokerRepository brokerRepository) {
        this.brokerRepository = brokerRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        // 1. Forzar la creación de la colección 'mqtt_brokers'
        // Esto también fuerza la creación de 'readings' y otros si la propiedad auto-index-creation está activa.
        if (brokerRepository.count() == 0) {

            System.out.println(">>> [DATA INIT] Colección mqtt_brokers vacía. Creando entrada inicial.");

            // 2. Crear el documento inicial (DEBES REEMPLAZAR CON TU URL/PUERTO REAL DE NGROK)
            MqttBroker initialBroker = new MqttBroker();
            initialBroker.setName("Broker-Local-Luis");

            // Reemplazar con la URL y Puerto actuales de tu ngrok
            initialBroker.setUrl("192.168.100.2");
            initialBroker.setPort(1883);

            // La bandera se pone en true para que el MqttDiscoveryService se conecte inmediatamente
            initialBroker.setActive(true);

            // 3. Guardar el documento
            brokerRepository.save(initialBroker);

            System.out.println(">>> [DATA INIT] Documento del Broker insertado. Spring se conectará en 30s.");
        }
    }
}