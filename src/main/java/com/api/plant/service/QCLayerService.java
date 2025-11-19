package com.api.plant.service;

import com.api.plant.entity.PlantDevice;
import com.api.plant.entity.Reading;
import com.api.plant.entity.Reading.QcStatus;
import com.api.plant.repository.ReadingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class QCLayerService {

    private static final Logger log = LoggerFactory.getLogger(QCLayerService.class);

    private final ReadingRepository readingRepository;
    private final ObjectMapper objectMapper;
    private final MqttTopicService mqttTopicService;
    // Nota: MqttTopicService se mantiene en el constructor aunque no se use en QC,
    // para mantener la inyección de dependencias si se usa en otro lugar.

    // Constante Lógica: Máxima variación de humedad (SUELO) permitida en el tiempo
    // ⚠️ Se aplica a la humedad del suelo, ya que es la más crítica para detectar fallas.
    private static final Double MAX_HUMIDITY_RATE_CHANGE = 15.0; // 15 puntos de % de humedad
    // Constante Lógica: Tiempo máximo para considerar el dato anterior (ej. 10 minutos)
    private static final Long MAX_TIME_DIFF_MINUTES = 10L;

    public QCLayerService(ReadingRepository readingRepository, ObjectMapper objectMapper, MqttTopicService mqttTopicService) {
        this.readingRepository = readingRepository;
        this.objectMapper = objectMapper;
        this.mqttTopicService = mqttTopicService;
    }

    /**
     * Aplica la técnica de Validación de Límites Físicos y Lógicos (Tasa de Cambio).
     * @param jsonPayload El payload JSON original recibido por MQTT.
     * @param device El dispositivo asociado (contiene plantId y userId).
     * @return El objeto Reading con su QcStatus definido (VALID, OUT_OF_RANGE, RATE_ERROR, etc.).
     */
    public Reading applyQualityCheck(String jsonPayload, PlantDevice device) {

        Reading currentReading;
        try {
            // 1. Deserialización: Mapear JSON a Objeto Reading
            currentReading = objectMapper.readValue(jsonPayload, Reading.class);
            // Inyectar metadatos cruciales para el resto de la lógica
            currentReading.setPlantId(device.getPlantId());
            currentReading.setUserId(device.getOwnerId());

        } catch (JsonProcessingException e) {
            log.error("❌ QC: Fallo en la deserialización del payload JSON de {}.", device.getPlantId(), e);
            Reading errorReading = new Reading();
            errorReading.setQcStatus(QcStatus.QC_ERROR);
            return errorReading;
        }

        // --- VALIDACIÓN 1: LÍMITES FÍSICOS ABSOLUTOS ---
        // Se usan valores ABSOLUTOS (ej. la humedad no puede ser > 100 ni < 0)
        if (!validatePhysicalLimits(currentReading)) {
            currentReading.setQcStatus(QcStatus.OUT_OF_RANGE);
            log.warn("🚨 QC: Dato de {} descartado por límites físicos (Temp: {}, HumAmb: {}, HumSuelo: {}).",
                    device.getPlantId(), currentReading.getTempC(), currentReading.getAmbientHumidity(), currentReading.getSoilHumidity());
            return currentReading;
        }

        // --- VALIDACIÓN 2: TASA DE CAMBIO LÓGICA (Solo si pasa la física) ---
        // Usa la última lectura VÁLIDA guardada en la base de datos
        if (!validateRateOfChange(currentReading)) {
            currentReading.setQcStatus(QcStatus.RATE_ERROR);
            log.warn("🛑 QC: Dato de {} descartado por salto brusco (Rate Error). Humedad de Suelo: {}",
                    device.getPlantId(), currentReading.getSoilHumidity());
            return currentReading;
        }

        // Si pasa ambas validaciones
        currentReading.setQcStatus(QcStatus.VALID);
        return currentReading;
    }

    /**
     * Regla 1: Descartar valores fuera de los límites físicos universales.
     */
    private boolean validatePhysicalLimits(Reading reading) {

        // 1. Humedad Ambiental y de Suelo (debe estar entre 0 y 100)
        if (reading.getAmbientHumidity() == null || reading.getAmbientHumidity() < 0 || reading.getAmbientHumidity() > 100) return false;
        if (reading.getSoilHumidity() == null || reading.getSoilHumidity() < 0 || reading.getSoilHumidity() > 100) return false;

        // 2. Temperatura (ej. -20C a 60C)
        if (reading.getTempC() == null || reading.getTempC() < -20.0 || reading.getTempC() > 60.0) return false;

        // 3. Luz (no puede ser negativa)
        if (reading.getLightLux() == null || reading.getLightLux() < 0) return false;

        return true;
    }

    /**
     * Regla 2: Detectar outliers que indican un sensor defectuoso (Tasa de Cambio).
     * Compara la lectura de HUMEDAD DE SUELO (métrica más volátil/crítica) con la última VÁLIDA.
     */
    private boolean validateRateOfChange(Reading currentReading) {

        // Buscar la última lectura VÁLIDA para esta planta/dispositivo
        Optional<Reading> lastValidReadingOpt = readingRepository.findTopByPlantIdAndQcStatusOrderByTimestampDesc(
                currentReading.getPlantId(), QcStatus.VALID
        );

        if (lastValidReadingOpt.isPresent()) {
            Reading lastReading = lastValidReadingOpt.get();

            // 1. Validar la diferencia de tiempo: No comparar si los datos son muy viejos.
            long timeDiff = ChronoUnit.MINUTES.between(lastReading.getTimestamp(), currentReading.getTimestamp());
            if (timeDiff > MAX_TIME_DIFF_MINUTES) {
                // Si ha pasado mucho tiempo (> 10 minutos), no aplicamos la regla de tasa de cambio.
                return true;
            }

            // 2. Aplicar la Tasa de Cambio al parámetro más sensible: HUMEDAD DE SUELO
            // Se asume que el cambio de humedad de suelo debe ser gradual.
            double currentSoilHumidity = currentReading.getSoilHumidity();
            double lastSoilHumidity = lastReading.getSoilHumidity();

            double absoluteChange = Math.abs(currentSoilHumidity - lastSoilHumidity);

            // Si el cambio de Humedad de Suelo es > 15 puntos porcentuales en menos de 10 minutos.
            if (absoluteChange > MAX_HUMIDITY_RATE_CHANGE) {
                return false; // OUTLIER detectado.
            }
        }
        // Si no hay lectura previa o el cambio es aceptable
        return true;
    }
}