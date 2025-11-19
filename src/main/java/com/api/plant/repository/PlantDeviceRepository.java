package com.api.plant.repository;

import com.api.plant.entity.PlantDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PlantDeviceRepository extends MongoRepository<PlantDevice, String> {


    // 1. Usado para el Advisor y comandos
    Optional<PlantDevice> findByPlantId(String plantId);

    // 2. Usado para listar las plantas del usuario (DeviceService.getDevicesByOwner)
    List<PlantDevice> findByOwnerId(String ownerId);


    /**
     * Busca un dispositivo por su dirección física (MAC Address).
     * Esto es CRUCIAL para el proceso de Device Provisioning (descubrimiento).
     * @param macAddress La dirección MAC del dispositivo ESP32.
     * @return Un Optional<PlantDevice> si se encuentra.
     */
    Optional<PlantDevice> findByMacAddress(String macAddress);


    /**
     * Busca dispositivos que están en "Modo Cero":
     * Tienen una MAC registrada (fueron descubiertos)
     * PERO AÚN no tienen un dueño asignado (ownerId es nulo).
     */
    List<PlantDevice> findByOwnerIdIsNullAndMacAddressIsNotNull();

}