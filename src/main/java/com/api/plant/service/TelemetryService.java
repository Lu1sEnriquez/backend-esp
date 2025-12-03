package com.api.plant.service;

import com.api.plant.config.InfluxConstants;
import com.api.plant.dto.socket.WebSocketMessage;
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
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class TelemetryService {

    public enum WebSocketMessageType {
        TELEMETRY,
        PUMP_EVENT,
        ALERT
    }

    private static final String DEFAULT_EMAIL = "izzyanimal573@gmail.com";
    private static final String EMAIL_SUBJECT_PREFIX = "[IoT ";
    private static final String EMAIL_SUBJECT_SUFFIX = "] ";

    // --- INYECCIÓN DE DEPENDENCIAS ---
    @Autowired private InfluxDBClient influxDBClient;

    // 🔥 CAMBIO: Inyectamos nuestro servicio personalizado en lugar de JavaMailSender directo
    @Autowired private EmailService emailService;

    @Autowired private MqttTopicService mqttTopicService;
    @Autowired private PlantDeviceRepository deviceRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private PlantAlertRepository alertRepository;

    @Autowired
    @Qualifier("mqttOutboundChannel")
    private MessageChannel mqttOutboundChannel;

    @Value("${influxdb.bucket}") private String bucket;
    @Value("${influxdb.org}") private String org;
    @Value("${device.thresholds.humiditySoil.min}") private int minSoilHumidity;
    @Value("${device.thresholds.temperature.max}") private double maxTemp;

    @Value("${spring.mail.username:" + DEFAULT_EMAIL + "}")
    private String defaultEmail;

    // ==========================================
    // METODO PRINCIPAL
    // ==========================================
    public void processAndSave(Reading reading) {
        convertPumpStateToBoolean(reading);
        System.out.println(" reading: " + reading.getPlantId()+" humidity: "+reading.getAmbientHumidity()+" humidity soil: "
                +reading.getSoilHumidity()+" tempC: "+reading.getTempC()+" lux: "+reading.getLightLux());
        if (reading.getMsgType() == Reading.MessageType.EVENT) {
            System.out.println("💧 Evento de Bomba Recibido: " + reading.isPumpOn());
            reading.setQcStatus(Reading.QcStatus.EVENT);
            saveToInflux(reading);
            sendWebSocketUpdate(reading.getPlantId(), WebSocketMessageType.PUMP_EVENT, reading);
            updateDeviceState(reading);
            return;
        }

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
    // LÓGICA DE ALERTA (MODIFICADO PARA HTML)
    // ==========================================
    private void runAdvisorLogic(Reading r) {
        r.setAdvisorResult(Reading.AdvisorResult.INFO);

        // 1. Obtener el correo destino UNA sola vez
        String targetEmail = getTargetEmail(r.getPlantId());

        if (r.getSoilHumidity() != null && r.getSoilHumidity() < minSoilHumidity) {
            r.setAdvisorResult(Reading.AdvisorResult.CRITICA);
            System.out.println("⚠️ ALERTA CRÍTICA: Suelo Seco (" + r.getSoilHumidity() + "%).");

            // WebSocket
            sendWebSocketUpdate(r.getPlantId(), WebSocketMessageType.ALERT, r);

            // Persistencia
            saveAlertToMongo(r, "CRITICA", "Humedad crítica: " + r.getSoilHumidity() + "%", "SOIL_HUMIDITY", r.getSoilHumidity());

            // Actuación
            String commandTopic = mqttTopicService.getDeviceCommandTopic(r.getPlantId());
            sendCommand(commandTopic, "{\"cmd\": \"RIEGO\"}");

            // 🔥 EMAIL HTML MEJORADO
            // Usamos el servicio inyectado para enviar HTML con colores y tabla
            emailService.enviarAlertaHtml(
                    targetEmail,
                    EMAIL_SUBJECT_PREFIX + "URGENTE: Riego Activado" + EMAIL_SUBJECT_SUFFIX,
                    "El sistema ha detectado humedad crítica y ha activado el riego automático.",
                    "CRITICA", // Esto activará la clase CSS roja en el template
                    r.getPlantId(),
                    r // Pasamos el objeto lectura para llenar la tabla
            );
        }
        else if (r.getTempC() != null && r.getTempC() > maxTemp) {
            r.setAdvisorResult(Reading.AdvisorResult.ALERTA);

            // WebSocket
            sendWebSocketUpdate(r.getPlantId(), WebSocketMessageType.ALERT, r);

            // Persistencia
            saveAlertToMongo(r, "ALERTA", "Temperatura alta: " + r.getTempC() + "°C", "TEMPERATURE", r.getTempC());

            // 🔥 EMAIL HTML MEJORADO
            emailService.enviarAlertaHtml(
                    targetEmail,
                    EMAIL_SUBJECT_PREFIX + "Alerta de Calor" + EMAIL_SUBJECT_SUFFIX,
                    "La temperatura ambiente ha superado el umbral seguro.",
                    "ALERTA", // Esto activará la clase CSS naranja
                    r.getPlantId(),
                    r
            );
        }
    }

    // ==========================================
    // MÉTODOS AUXILIARES
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

    private void convertPumpStateToBoolean(Reading reading) {
        try {
            if (reading.getPumpOn() == null) reading.setPumpOn(false);
        } catch (Exception e) {
            reading.setPumpOn(false);
        }
    }

    private void sendWebSocketUpdate(String plantId, WebSocketMessageType type, Reading reading) {
        try {
            WebSocketMessage<?> message = switch (type) {
                case TELEMETRY -> WebSocketMessage.createTelemetry(plantId, reading);
                case PUMP_EVENT -> WebSocketMessage.createPumpEvent(plantId, reading);
                case ALERT -> WebSocketMessage.createAlert(plantId, reading);
            };
            String destination = mqttTopicService.getWebSocketTopic(plantId);
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            System.err.println("❌ Error enviando WebSocket: " + e.getMessage());
        }
    }

    private void saveToInflux(Reading r) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            Point point = Point.measurement(InfluxConstants.MEASUREMENT_SENSORES_PLANTA)
                    .addTag(InfluxConstants.TAG_PLANT_ID, r.getPlantId())
                    .addTag(InfluxConstants.TAG_STATUS_QC, r.getQcStatus().name())
                    .time(Instant.now(), WritePrecision.NS);

            if (r.getTempC() != null) point.addField(InfluxConstants.FIELD_TEMPERATURA, r.getTempC());
            if (r.getAmbientHumidity() != null) point.addField(InfluxConstants.FIELD_HUMEDAD_AIRE, r.getAmbientHumidity());
            if (r.getSoilHumidity() != null) point.addField(InfluxConstants.FIELD_HUMEDAD_SUELO, r.getSoilHumidity());
            if (r.getLightLux() != null) point.addField(InfluxConstants.FIELD_LUZ, r.getLightLux());
            if (r.getPumpOn() != null) point.addField(InfluxConstants.FIELD_BOMBA_ESTADO, r.isPumpOn() ? 1 : 0);
            point.addField(InfluxConstants.FIELD_ALERTA_ACTIVA, r.getAdvisorResult() == Reading.AdvisorResult.CRITICA ? 1 : 0);

            writeApi.writePoint(bucket, org, point);
        } catch (Exception e) {
            System.err.println("❌ Error Influx: " + e.getMessage());
        }
    }

    private boolean runQualityControl(Reading r) {
        if (r.getTempC() != null && (r.getTempC() > 80 || r.getTempC() < -20)) return false;
        if (r.getSoilHumidity() != null && (r.getSoilHumidity() < 0 || r.getSoilHumidity() > 100)) return false;
        return true;
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
        // Lógica para actualizar estado en MongoDB
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
}