package com.api.plant.service;

import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.PlantDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // Importante
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DeviceAuthService {

    // 2. INICIALIZAR EL LOGGER
    private static final Logger log = LoggerFactory.getLogger(DeviceAuthService.class);

    private final PlantDeviceRepository plantDeviceRepository;
    private final MqttTopicService mqttTopicService;

    // --- 1. Inyectar las credenciales del Backend ---
    @Value("${mqtt.backend.username}")
    private String backendUsername;

    @Value("${mqtt.backend.password}")
    private String backendPassword;

    // --- 2. Inyectar credenciales de Provisioning (o hardcodear) ---
    // (Asegúrate de que estas coincidan con el firmware de tu ESP32)
    private final String PROVISION_USER = "provision_user";
    private final String PROVISION_PASS = "provision_pass";

    public DeviceAuthService(PlantDeviceRepository plantDeviceRepository, MqttTopicService mqttTopicService) {
        this.plantDeviceRepository = plantDeviceRepository;
        this.mqttTopicService = mqttTopicService;
    }

    /**
     * Autentica a cualquier cliente (Backend, Provisioning, o Dispositivo Registrado).
     */
    public boolean authenticateDevice(String username, String password) {

        // 3. AÑADIR LOGS DE DEPURACIÓN
        log.info("--- INICIO DE AUTENTICACIÓN ---");
        log.info("AUTH: Intentando autenticar usuario: [{}], Contraseña: [{}]", username, password);

        // REGLA 1: Backend
        if (username.equals(backendUsername)) {
            log.debug("AUTH: Coincide con REGLA 1 (Backend)");
            boolean match = password.equals(backendPassword);
            log.info("AUTH: Resultado de REGLA 1: {}", match);
            log.info("--- FIN DE AUTENTICACIÓN ---");
            return match;
        }

        // REGLA 2: Provisioning
        if (username.equals(PROVISION_USER)) {
            log.debug("AUTH: Coincide con REGLA 2 (Provisioning)");
            log.debug("AUTH: Recibido: [{}], Esperado: [{}]", password, PROVISION_PASS);
            boolean match = password.equals(PROVISION_PASS);

            if(!match) {
                log.error("¡FALLO DE AUTENTICACIÓN DE PROVISIONING! Las contraseñas no coinciden.");
            }

            log.info("AUTH: Resultado de REGLA 2: {}", match);
            log.info("--- FIN DE AUTENTICACIÓN ---");
            return match;
        }

        // REGLA 3: Dispositivo Registrado
        Optional<PlantDevice> deviceOpt = plantDeviceRepository.findByPlantId(username);
        if (deviceOpt.isPresent()) {
            log.debug("AUTH: Coincide con REGLA 3 (Dispositivo)");
            boolean match = password.equals(deviceOpt.get().getMqttPassword());
            log.info("AUTH: Resultado de REGLA 3: {}", match);
            log.info("--- FIN DE AUTENTICACIÓN ---");
            return match;
        }

        // REGLA 4: Rechazar
        log.warn("AUTH: Usuario [{}] no coincidió con ninguna regla. RECHAZADO.", username);
        log.info("--- FIN DE AUTENTICACIÓN ---");
        return false;
    }

    /**
     * Autoriza el acceso a tópicos (ACLs) para los 3 tipos de usuarios.
     * (Logs de depuración añadidos)
     */
    public boolean authorizeTopic(String username, String topic, int access) {
        // access 1 = SUB (Suscribir), 2 = PUB (Publicar)
        String accessType = (access == 1) ? "SUSCRIBIRSE" : "PUBLICAR";

        log.info("--- INICIO DE AUTORIZACIÓN (ACL) ---");
        log.info("ACL: Usuario [{}] quiere [{}] en el tópico [{}]", username, accessType, topic);

        // REGLA 1: Reglas para el Backend de Spring Boot
        if (username.equals(backendUsername)) {
            log.debug("ACL: Coincide con REGLA 1 (Backend)");
            // El backend puede hacer todo en 'planta/' y 'control/'
            if (topic.startsWith("planta/") || topic.startsWith("control/") || topic.startsWith("test/inbox")) {
                log.info("ACL: Resultado de REGLA 1: true (Permitido)");
                log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                return true;
            }
            log.warn("ACL: Resultado de REGLA 1: false (Tópico no permitido para Backend: {})", topic);
            log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
            return false;
        }
// REGLA 2: Reglas para el usuario genérico de provisioning (CORREGIDA)
        // REGLA 2: Reglas para el usuario genérico de provisioning (CORREGIDA)
        if (username.equals(PROVISION_USER)) {
            log.debug("ACL: Coincide con REGLA 2 (Provisioning)");
            log.debug("ACL: Valor de ACCESS recibido: [{}]", access);

            // REGLA 2.A: Permiso para publicar (Access = 2)
            if (access == 2) {
                if (topic.startsWith("control/provisioning/discovery")) {
                    log.info("ACL: Resultado de REGLA 2.A (Discovery): true (Permitido)");
                    log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                    return true;
                }
            }

            // REGLA 2.B: Permiso para suscribirse (Access = 1 o 4)
            // --- ¡CORRECCIÓN CRÍTICA! ---
            if (access == 1 || access == 4) { // <-- Aceptamos 1 (estándar) y 4 (lo que estás viendo)

                log.info("ACL: Detectada acción de SUSCRIBIRSE (Access={})", access);

                // Tópico: "control/provisioning/device/"
                if (topic.startsWith("control/provisioning/device/")) {
                    log.info("ACL: Resultado de REGLA 2.B (Suscripción MAC): true (Permitido)");
                    log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                    return true;
                }

                // Tópico: "test/inbox"
                if (topic.startsWith("test/inbox")) {
                    log.info("ACL: Resultado de REGLA 2.C (Suscripción TEST): true (Permitido)");
                    log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                    return true;
                }
            }

            // REGLA 2.C: Permiso para Probar (Publicar)
            // (Movemos la publicación de test/inbox fuera del bloque 'access == 2'
            //  para que la REGLA 2.C de tu código anterior funcione)
            if (topic.startsWith("test/inbox")) {
                log.info("ACL: Resultado de REGLA 2.C (Test Inbox): true (Permitido)");
                log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                return true;
            }

            // Si no es ninguna de esas acciones, rechazar.
            log.warn("ACL: Resultado de REGLA 2: false (Acción [{}] en Tópico [{}] RECHAZADA para provision_user)", accessType, topic);
            log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
            return false;
        }

        // REGLA 3: Reglas para dispositivos vinculados
        Optional<PlantDevice> deviceOpt = plantDeviceRepository.findByPlantId(username);
        if (deviceOpt.isPresent()) {
            log.debug("ACL: Coincide con REGLA 3 (Dispositivo Vinculado)");

            // El dispositivo solo puede publicar en su propio tópico de datos
            String dataTopic = mqttTopicService.getDeviceDataTopic(username);
            if (access == 2 && topic.equals(dataTopic)) {
                log.info("ACL: Resultado de REGLA 3: true (Publicación de datos permitida)");
                log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                return true;
            }

            // El dispositivo solo puede suscribirse a su propio tópico de comandos
            String commandTopic = mqttTopicService.getDeviceCommandTopic(username);
            if (access == 1 && topic.equals(commandTopic)) {
                log.info("ACL: Resultado de REGLA 3: true (Suscripción a comandos permitida)");
                log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
                return true;
            }

            log.warn("ACL: Resultado de REGLA 3: false (Acción [{}] en Tópico [{}] RECHAZADA para dispositivo vinculado)", accessType, topic);
            log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
            return false;
        }

        log.error("ACL: Usuario [{}] no coincidió con ninguna regla ACL. RECHAZADO.", username);
        log.info("--- FIN DE AUTORIZACIÓN (ACL) ---");
        return false;
    }
}