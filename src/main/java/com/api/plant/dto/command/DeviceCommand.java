package com.api.plant.dto.command;

/**
 * Enum que lista todos los comandos válidos que el Backend puede enviar al ESP32
 * para que actúe sobre los actuadores o el sistema.
 */
public enum DeviceCommand {

    RIEGO,


    CONFIG_SET,

    // Comando de administración para reiniciar la placa
    REBOOT,

    // Comando para resetear/borrar las credenciales de conexión (usado para desvincular)
    CONFIG_RESET,

    // Comando para forzar el envío de una lectura de sensor inmediata
    FORCE_READ,

    // Ejemplo de comando futuro para un actuador de luz
    SET_LIGHT_COLOR
}