package com.api.plant.service;

import com.api.plant.entity.AppUser;
import com.api.plant.entity.PlantDevice;
import com.api.plant.entity.Reading;
import com.api.plant.repository.AppUserRepository;
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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class TelemetryService {

    // --- 1. INYECCIÓN DE DEPENDENCIAS ---

    @Autowired
    private InfluxDBClient influxDBClient;

    @Autowired
    private JavaMailSender mailSender;

    // Inyectamos el canal de SALIDA para enviar comandos MQTT
    @Autowired
    @Qualifier("mqttOutboundChannel")
    private MessageChannel mqttOutboundChannel;

    // Inyectamos el servicio de Tópicos para no tener strings "quemados"
    @Autowired
    private MqttTopicService mqttTopicService;

    @Autowired
    private PlantDeviceRepository deviceRepository;
    @Autowired
    private AppUserRepository userRepository;

    // --- 2. VARIABLES DE CONFIGURACIÓN (Properties) ---

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    @Value("${device.thresholds.humiditySoil.min}")
    private int minSoilHumidity; // 35%

    @Value("${device.thresholds.temperature.max}")
    private double maxTemp;      // 38.0°C

    // ==========================================
    // MÉTODO PRINCIPAL (Orquestador)
    // ==========================================
    public void processAndSave(Reading reading) {

        // Paso 1: Control de Calidad (QC)
        if (!runQualityControl(reading)) {
            System.out.println("🚫 Dato descartado por QC (Fuera de Rango o Error): " + reading.getPlantId());
            reading.setQcStatus(Reading.QcStatus.OUT_OF_RANGE);
        } else {
            reading.setQcStatus(Reading.QcStatus.VALID);
        }

        // Paso 2: Lógica de Negocio (Advisor)
        if (reading.getQcStatus() == Reading.QcStatus.VALID) {
            runAdvisorLogic(reading);
        }

        // Paso 3: Guardar en InfluxDB
        saveToInflux(reading);
    }

    // ==========================================
    // LÓGICA INTERNA
    // ==========================================

    private boolean runQualityControl(Reading r) {
        // Validación: Temperatura imposible
        if (r.getTempC() != null && (r.getTempC() > 80 || r.getTempC() < -20)) {
            return false;
        }
        // Validación: Humedad negativa o > 100
        if (r.getSoilHumidity() != null && (r.getSoilHumidity() < 0 || r.getSoilHumidity() > 100)) {
            return false;
        }
        return true;
    }


    private void runAdvisorLogic(Reading r) {
        r.setAdvisorResult(Reading.AdvisorResult.INFO);

        // --- CASO 1: EMERGENCIA DE RIEGO ---
        if (r.getSoilHumidity() != null && r.getSoilHumidity() < minSoilHumidity) {
            r.setAdvisorResult(Reading.AdvisorResult.CRITICA);

            System.out.println("⚠️ ALERTA CRÍTICA: Suelo Seco (" + r.getSoilHumidity() + "%). Iniciando protocolo de riego.");

            // Acción A: Enviar comando MQTT al ESP32
            sendCommand(r.getPlantId(), "{\"cmd\": \"RIEGO\"}");

            // Acción B: Notificar al usuario
            sendEmailAlert(r.getPlantId(), "URGENTE: Riego Activado",
                    "La humedad del suelo bajó a " + r.getSoilHumidity() + "%. Hemos activado la bomba automáticamente.");
        }

        // --- CASO 2: ALERTA AMBIENTAL ---
        else if (r.getTempC() != null && r.getTempC() > maxTemp) {
            r.setAdvisorResult(Reading.AdvisorResult.ALERTA);
            System.out.println("🔥 ALERTA: Temperatura Alta (" + r.getTempC() + "°C)");

            sendEmailAlert(r.getPlantId(), "Alerta de Calor",
                    "La temperatura ambiente es crítica: " + r.getTempC() + "°C. Mueve la planta a la sombra.");
        }
    }


    private void saveToInflux(Reading r) {
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

            Point point = Point.measurement("sensores_planta")
                    .addTag("plantId", r.getPlantId())
                    .addTag("status_qc", r.getQcStatus().name())
                    .addField("temperatura", r.getTempC())
                    .addField("humedad_aire", r.getAmbientHumidity())
                    .addField("humedad_suelo", r.getSoilHumidity())
                    .addField("luz", r.getLightLux())
                    .addField("alerta_activa", r.getAdvisorResult() == Reading.AdvisorResult.CRITICA ? 1 : 0)
                    .time(Instant.now(), WritePrecision.NS);

            writeApi.writePoint(bucket, org, point);
            System.out.println("✅ Dato persistido en InfluxDB. ID: " + r.getPlantId());

        } catch (Exception e) {
            System.err.println("❌ Error escribiendo en InfluxDB: " + e.getMessage());
        }
    }


    private void sendCommand(String plantId, String jsonCommand) {
        // USO DE SERVICIO CENTRALIZADO PARA OBTENER EL TÓPICO
        String targetTopic = mqttTopicService.getDeviceCommandTopic(plantId);

        try {
            mqttOutboundChannel.send(MessageBuilder
                    .withPayload(jsonCommand)
                    .setHeader(MqttHeaders.TOPIC, targetTopic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build());

            System.out.println("📤 Comando enviado: " + jsonCommand + " -> " + targetTopic);
        } catch (Exception e) {
            System.err.println("❌ Error enviando comando MQTT: " + e.getMessage());
        }
    }


    // ==========================================
    // 🆕 MÉTODO MODIFICADO: CORREO DINÁMICO
    // ==========================================
    private void sendEmailAlert(String plantId, String subject, String text) {
        try {
            String targetEmail = "izzyanimal573@gmail.com"; // Correo por defecto (Administrador)

            // 1. Buscar el dispositivo
            Optional<PlantDevice> deviceOpt = deviceRepository.findByPlantId(plantId);

            if (deviceOpt.isPresent()) {
                String ownerId = deviceOpt.get().getOwnerId();

                // 2. Si tiene dueño, buscar al usuario
                if (ownerId != null) {
                    Optional<AppUser> userOpt = userRepository.findById(ownerId);
                    if (userOpt.isPresent()) {
                        targetEmail = userOpt.get().getEmail(); // ✅ BINGO: Usamos el correo del usuario
                        System.out.println("📧 Destinatario encontrado: " + targetEmail);
                    }
                }
            }

            // 3. Enviar el correo
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(targetEmail);
            message.setSubject("[IoT " + plantId + "] " + subject);
            message.setText(text + "\n\n- Sistema de Monitoreo Automático");

            mailSender.send(message);
            System.out.println("📧 Correo enviado a: " + targetEmail);

        } catch (Exception e) {
            System.err.println("❌ Error enviando correo: " + e.getMessage());
        }
    }
}