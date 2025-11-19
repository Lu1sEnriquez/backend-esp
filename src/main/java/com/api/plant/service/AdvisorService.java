package com.api.plant.service;

import com.api.plant.entity.PlantDevice;
import com.api.plant.entity.Reading;
import com.api.plant.entity.Reading.AdvisorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AdvisorService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorService.class);

    private final NotificationService notificationService;

    public AdvisorService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Evalúa una lectura válida usando los umbrales específicos del dispositivo.
     * @param reading La lectura con QC_STATUS: VALID.
     * @param device El dispositivo asociado, que contiene TODOS los umbrales.
     * @return La lectura actualizada con el resultado del Advisor.
     */
    public Reading evaluateReading(Reading reading, PlantDevice device) {

        // --- 0. OBTENCIÓN Y VALIDACIÓN DE UMBRALES ---
        Integer minSoilHumidity = device.getMinSoilHumidity();
        Integer maxSoilHumidity = device.getMaxSoilHumidity();
        Integer minAmbientHumidity = device.getMinHumidity();
        Integer maxAmbientHumidity = device.getMaxHumidity();
        Double minTempC = device.getMinTempC();
        Double maxTempC = device.getMaxTempC();
        Integer minLightLux = device.getMinLightLux();
        Integer maxLightLux = device.getMaxLightLux();

        // 🛑 CORRECCIÓN DE INTEGRIDAD: Se incluye la verificación de los
        // umbrales de humedad ambiental (minAmbientHumidity, maxAmbientHumidity)
        // ya que se usan para disparar ALERTAs.
        if (minSoilHumidity == null || maxSoilHumidity == null ||
                minAmbientHumidity == null || maxAmbientHumidity == null || // <--- ¡AÑADIDOS!
                minTempC == null || maxTempC == null ||
                minLightLux == null || maxLightLux == null) {

            log.error("❌ ERROR ADVISOR: Umbrales incompletos para la planta {}. Faltan datos de configuración cruciales.", device.getPlantId());
            reading.setAdvisorResult(AdvisorResult.INFO);
            return reading;
        }


        // =======================================================
        // --- 1. EVALUACIÓN CRÍTICA: RIEGO (Humedad de SUELO) ---
        // CRÍTICA: Humedad de Suelo < Mínima
        // =======================================================
        if (reading.getSoilHumidity() < minSoilHumidity) {

            reading.setAdvisorResult(AdvisorResult.CRITICA);
            log.error("🛑 CRÍTICO en {}: Humedad de SUELO ({}) < Mínimo ({}). REQUIERE RIEGO.",
                    reading.getPlantId(), reading.getSoilHumidity(), minSoilHumidity);

            // ACCIÓN: Disparar notificación de riego
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.CRITICA);

            return reading;
        }


        // =================================================================
        // --- 2. EVALUACIÓN DE ALERTA: CONDICIONES AMBIENTALES PELIGROSAS ---
        // =================================================================

        // A. Alerta por exceso de calor
        if (reading.getTempC() > maxTempC) {
            reading.setAdvisorResult(AdvisorResult.ALERTA);
            log.warn("🔥 ALERTA en {}: Temperatura ({}) > Máximo ({}). Riesgo de calor.",
                    reading.getPlantId(), reading.getTempC(), maxTempC);
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.ALERTA);
            return reading;
        }

        // B. Alerta por exceso de frío
        if (reading.getTempC() < minTempC) {
            reading.setAdvisorResult(AdvisorResult.ALERTA);
            log.warn("❄️ ALERTA en {}: Temperatura ({}) < Mínimo ({}). Riesgo de frío.",
                    reading.getPlantId(), reading.getTempC(), minTempC);
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.ALERTA);
            return reading;
        }

        // C. Alerta por exceso de luz (quemadura)
        if (reading.getLightLux() > maxLightLux) {
            reading.setAdvisorResult(AdvisorResult.ALERTA);
            log.warn("🔆 ALERTA en {}: Luz ({}) > Máximo ({}). Riesgo de quemadura.",
                    reading.getPlantId(), reading.getLightLux(), maxLightLux);
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.ALERTA);
            return reading;
        }

        // D. Alerta por exceso de humedad de suelo (encharcamiento/raíces podridas)
        if (reading.getSoilHumidity() > maxSoilHumidity) {
            reading.setAdvisorResult(AdvisorResult.ALERTA);
            log.warn("💧 ALERTA en {}: Humedad de SUELO ({}) > Máximo ({}). Riesgo de encharcamiento.",
                    reading.getPlantId(), reading.getSoilHumidity(), maxSoilHumidity);
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.ALERTA);
            return reading;
        }

        // E. Alerta por humedad ambiental alta
        if (reading.getAmbientHumidity() > maxAmbientHumidity) {
            reading.setAdvisorResult(AdvisorResult.ALERTA);
            log.warn("💨 ALERTA en {}: Humedad AMBIENTAL ({}) > Máximo ({}). Riesgo de hongo.",
                    reading.getPlantId(), reading.getAmbientHumidity(), maxAmbientHumidity);
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.ALERTA);
            return reading;
        }

        // F. Alerta por humedad ambiental baja (aire seco)
        if (reading.getAmbientHumidity() < minAmbientHumidity) {
            reading.setAdvisorResult(AdvisorResult.ALERTA);
            log.warn("🏜️ ALERTA en {}: Humedad AMBIENTAL ({}) < Mínimo ({}). Riesgo de aire seco.",
                    reading.getPlantId(), reading.getAmbientHumidity(), minAmbientHumidity);
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.ALERTA);
            return reading;
        }


        // ======================================================================
        // --- 3. EVALUACIÓN DE RECOMENDACIÓN (Luz por debajo del mínimo) ---
        // ======================================================================
        if (reading.getLightLux() < minLightLux) {
            reading.setAdvisorResult(AdvisorResult.RECOMENDACION);
            log.info("💡 RECOMENDACIÓN en {}: Luz ({}) por debajo del mínimo sostenido ({}).",
                    reading.getPlantId(), reading.getLightLux(), minLightLux);

            // ACCIÓN: Aviso discreto (feed de la aplicación)
            notificationService.sendAlert(reading, device.getOwnerId(), AdvisorResult.RECOMENDACION);

            return reading;
        }

        // --- 4. ESTADO NORMAL ---
        reading.setAdvisorResult(AdvisorResult.INFO);
        return reading;
    }
}