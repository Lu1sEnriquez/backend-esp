package com.api.plant.dto.device;

// No se necesitan imports de Spring Data o seguridad
public record DeviceRegistrationRequest(
        String name,
        String description
) {

}