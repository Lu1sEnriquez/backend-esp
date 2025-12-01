package com.api.plant.service;

import com.api.plant.config.InfluxConstants;
import com.api.plant.dto.socket.WebSocketMessage;
import com.api.plant.dto.socket.TelemetryData;
import com.api.plant.dto.socket.PumpEvent;
import com.api.plant.dto.socket.Alert;
import com.api.plant.entity.AppUser;
import com.api.plant.entity.PlantAlert;
import com.api.plant.entity.PlantDevice;
import com.api.plant.entity.Reading;
import com.api.plant.repository.AppUserRepository;
import com.api.plant.repository.PlantAlertRepository;
import com.api.plant.repository.PlantDeviceRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class TelemetryService {

    // --- ENUM para tipos de WebSocket ---
    public enum WebSocketMessageType {
        TELEMETRY,  // Datos de sensores
        PUMP_EVENT, // Eventos de bomba
        ALERT       // Alertas del sistema
    }

    // --- CONSTANTES LOCALES ---
    private static final String DEFAULT_EMAIL = "izzyanimal573@gmail.com";
    private static final String EMAIL_SUBJECT_PREFIX = "[IoT ";
    private static final String EMAIL_SUBJECT_SUFFIX = "] ";

    // --- INYECCIÓN DE DEPENDENCIAS ---
    @Autowired private InfluxDBClient influxDBClient;
    @Autowired private JavaMailSender mailSender;
    @Autowired private MqttTopicService mqttTopicService;
    @Autowired private PlantDeviceRepository deviceRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private PlantAlertRepository alertRepository; // <--- AGREGAR ESTO
    @Autowired
    @Qualifier("mqttOutboundChannel")
    private MessageChannel mqttOutboundChannel;

    @Value("${influxdb.bucket}") private String bucket;
    @Value("${influxdb.org}") private String org;
    @Value("${device.thresholds.humiditySoil.min}") private int minSoilHumidity;
    @Value("${device.thresholds.temperature.max}") private double maxTemp;

    // 🔥 NUEVO: Usar el email de configuración de Spring
    @Value("${spring.mail.username:" + DEFAULT_EMAIL + "}")
    private String defaultEmail;

    // ==========================================
    // METODO PRINCIPAL MEJORADO
    // ==========================================
    public void processAndSave(Reading reading) {

        // 🔥 CONVERSIÓN SIMPLE DE STRING A BOOLEAN
        convertPumpStateToBoolean(reading);

        // CASO ESPECIAL: Si es un EVENTO (Bomba ON/OFF)
        if (reading.getMsgType() == Reading.MessageType.EVENT) {
            System.out.println("💧 Evento de Bomba Recibido: " + reading.isPumpOn());
            reading.setQcStatus(Reading.QcStatus.EVENT);

            saveToInflux(reading);
            sendWebSocketUpdate(reading.getPlantId(), WebSocketMessageType.PUMP_EVENT, reading);
            updateDeviceState(reading);
            return;
        }

        // FLUJO NORMAL (Lecturas de sensores)
        if (!runQualityControl(reading)) {
            System.out.println("🚫 Dato descartado por QC: " + reading.getPlantId());
            reading.setQcStatus(Reading.QcStatus.OUT_OF_RANGE);
        } else {
            reading.setQcStatus(Reading.QcStatus.VALID);
        }

        if (reading.getQcStatus() == Reading.QcStatus.VALID) {
            runAdvisorLogic(reading);
        }

        sendWebSocketUpdate(reading.getPlantId(), WebSocketMessageType.TELEMETRY, reading);
        saveToInflux(reading);
    }

    // ==========================================
    // MÉTODO SIMPLIFICADO: CONVERSIÓN A BOOLEAN
    // ==========================================
    private void convertPumpStateToBoolean(Reading reading) {
        try {
            if (reading.getPumpOn() == null) {
                reading.setPumpOn(false); // Por defecto OFF
            }
        } catch (Exception e) {
            System.err.println("❌ Error en pumpState: " + e.getMessage());
            reading.setPumpOn(false);
        }
    }

    // ==========================================
    // MÉTODO WEBSOCKET SIMPLIFICADO CON NUEVAS CLASES
    // ==========================================
    private void sendWebSocketUpdate(String plantId, WebSocketMessageType type, Reading reading) {
        try {
            WebSocketMessage<?> message = createWebSocketMessage(plantId, type, reading);
            // 🔥 USAR SERVICIO DE TÓPICOS
            String destination = mqttTopicService.getWebSocketTopic(plantId);
            messagingTemplate.convertAndSend(destination, message);

            System.out.println("📡 WebSocket enviado: " + type + " - PumpOn: " + reading.isPumpOn());

        } catch (Exception e) {
            System.err.println("❌ Error enviando WebSocket: " + e.getMessage());
        }
    }

    // ==========================================
    // MÉTODO PARA CREAR MENSAJES WEBSOCKET
    // ==========================================
    private WebSocketMessage<?> createWebSocketMessage(String plantId, WebSocketMessageType type, Reading reading) {
        return switch (type) {
            case TELEMETRY -> WebSocketMessage.createTelemetry(plantId, reading);
            case PUMP_EVENT -> WebSocketMessage.createPumpEvent(plantId, reading);
            case ALERT -> WebSocketMessage.createAlert(plantId, reading);
        };
    }

    // ==========================================
    // SAVE TO INFLUX MEJORADO CON CONSTANTES
    // ==========================================
    private void saveToInflux(Reading r) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

            Point point = Point.measurement(InfluxConstants.MEASUREMENT_SENSORES_PLANTA)
                    .addTag(InfluxConstants.TAG_PLANT_ID, r.getPlantId())
                    .addTag(InfluxConstants.TAG_STATUS_QC, r.getQcStatus().name())
                    .time(Instant.now(), WritePrecision.NS);

            // Campos de sensores usando constantes
            if (r.getTempC() != null)
                point.addField(InfluxConstants.FIELD_TEMPERATURA, r.getTempC());
            if (r.getAmbientHumidity() != null)
                point.addField(InfluxConstants.FIELD_HUMEDAD_AIRE, r.getAmbientHumidity());
            if (r.getSoilHumidity() != null)
                point.addField(InfluxConstants.FIELD_HUMEDAD_SUELO, r.getSoilHumidity());
            if (r.getLightLux() != null)
                point.addField(InfluxConstants.FIELD_LUZ, r.getLightLux());

            // Estado de bomba usando constantes
            if (r.getPumpOn() != null) {
                point.addField(InfluxConstants.FIELD_BOMBA_ESTADO, r.isPumpOn() ? 1 : 0);
            }

            // Alerta activa usando constantes
            point.addField(InfluxConstants.FIELD_ALERTA_ACTIVA,
                    r.getAdvisorResult() == Reading.AdvisorResult.CRITICA ? 1 : 0);

            writeApi.writePoint(bucket, org, point);
            System.out.println("✅ Guardado en InfluxDB: " + r.getPlantId()+ " tempC: "+r.getTempC() + "%, ambiente: "+r.getAmbientHumidity()+ "%, suelo: "+r.getSoilHumidity()+ " lux: "+r.getLightLux());

        } catch (Exception e) {
            System.err.println("❌ Error Influx: " + e.getMessage());
        }
    }

    // ==========================================
    // LÓGICA INTERNA
    // ==========================================
    private boolean runQualityControl(Reading r) {
        if (r.getTempC() != null && (r.getTempC() > 80 || r.getTempC() < -20)) return false;
        if (r.getSoilHumidity() != null && (r.getSoilHumidity() < 0 || r.getSoilHumidity() > 100)) return false;
        return true;
    }

    private void runAdvisorLogic(Reading r) {
        r.setAdvisorResult(Reading.AdvisorResult.INFO);

        if (r.getSoilHumidity() != null && r.getSoilHumidity() < minSoilHumidity) {
            r.setAdvisorResult(Reading.AdvisorResult.CRITICA);
            System.out.println("⚠️ ALERTA CRÍTICA: Suelo Seco (" + r.getSoilHumidity() + "%).");

            // 1. WebSocket (Tiempo Real)
            sendWebSocketUpdate(r.getPlantId(), WebSocketMessageType.ALERT, r);

            // 2. Persistencia (Historial) - NUEVO 🔥
            saveAlertToMongo(r, "CRITICA", "Humedad crítica: " + r.getSoilHumidity() + "%", "SOIL_HUMIDITY", r.getSoilHumidity());

            // 3. Actuación (Riego)
            String commandTopic = mqttTopicService.getDeviceCommandTopic(r.getPlantId());
            sendCommand(commandTopic, "{\"cmd\": \"RIEGO\"}");

            // 4. Email
            sendEmailAlert(r.getPlantId(), "URGENTE: Riego Activado", "Humedad baja...");
        }
        else if (r.getTempC() != null && r.getTempC() > maxTemp) {
            r.setAdvisorResult(Reading.AdvisorResult.ALERTA);

            // 1. WebSocket
            sendWebSocketUpdate(r.getPlantId(), WebSocketMessageType.ALERT, r);

            // 2. Persistencia - NUEVO 🔥
            saveAlertToMongo(r, "ALERTA", "Temperatura alta: " + r.getTempC() + "°C", "TEMPERATURE", r.getTempC());

            sendEmailAlert(r.getPlantId(), "Alerta de Calor", "Temp: " + r.getTempC());
        }
    }

    private void saveAlertToMongo(Reading r, String severity, String msg, String metric, double val) {
        try {
            PlantAlert alert = new PlantAlert(r.getPlantId(), severity, msg, metric, val);
            alertRepository.save(alert);
            System.out.println("💾 Alerta guardada en Mongo ID: " + alert.getId());
        } catch (Exception e) {
            System.err.println("❌ Error guardando alerta: " + e.getMessage());
        }
    }

    private void updateDeviceState(Reading r) {
        // Lógica para actualizar estado en MongoDB si es necesario
    }

    private void sendCommand(String topic, String jsonCommand) {
        try {
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(jsonCommand)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build());
            System.out.println("📤 Comando enviado a " + topic + ": " + jsonCommand);
        } catch (Exception e) {
            System.err.println("❌ Error enviando comando: " + e.getMessage());
        }
    }

    // ==========================================
    // SEND EMAIL ALERT MEJORADO
    // ==========================================
    private void sendEmailAlert(String plantId, String subject, String text) {
        try {
            // 🔥 USAR EMAIL DE CONFIGURACIÓN O BUSCAR EN BD
            String targetEmail = getTargetEmail(plantId);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(targetEmail);
            message.setSubject(buildEmailSubject(plantId, subject));
            message.setText(text);

            mailSender.send(message);
            System.out.println("📧 Email enviado a: " + targetEmail);

        } catch (Exception e) {
            System.err.println("❌ Error email: " + e.getMessage());
        }
    }

    // ==========================================
    // MÉTODOS PRIVADOS AUXILIARES
    // ==========================================
    private String getTargetEmail(String plantId) {
        try {
            Optional<PlantDevice> deviceOpt = deviceRepository.findByPlantId(plantId);
            if (deviceOpt.isPresent()) {
                String ownerId = deviceOpt.get().getOwnerId();
                if (ownerId != null) {
                    Optional<AppUser> userOpt = userRepository.findById(ownerId);
                    if (userOpt.isPresent()) {
                        return userOpt.get().getEmail();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ No se pudo obtener email del propietario, usando email por defecto");
        }
        return defaultEmail;
    }

    private String buildEmailSubject(String plantId, String subject) {
        return EMAIL_SUBJECT_PREFIX + plantId + EMAIL_SUBJECT_SUFFIX + subject;
    }
}