package com.api.plant.dto;

/**
 * DTO para recibir las credenciales de Login y Registro desde el Frontend.
 */
public record AuthRequest(
        String username,
        String password,
        String email // Opcional, solo para registro
) {}