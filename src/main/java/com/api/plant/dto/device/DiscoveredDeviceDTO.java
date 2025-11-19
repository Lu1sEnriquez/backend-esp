package com.api.plant.dto;

/**
 * DTO para representar un dispositivo que ha sido descubierto
 * pero aún no ha sido vinculado (reclamado) por un usuario.
 */
public record DiscoveredDeviceDTO(
        String macAddress, // El ID de hardware
        String temporaryName // ej. "Dispositivo Desconocido"
) {}