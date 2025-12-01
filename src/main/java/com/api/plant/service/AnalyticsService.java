package com.api.plant.service;

import com.api.plant.config.InfluxConstants;
import com.api.plant.dto.analytics.DashboardDtos.*;
import com.api.plant.entity.PlantDevice; // <--- Importar Entidad
import com.api.plant.repository.PlantDeviceRepository;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AnalyticsService {

    @Autowired
    private InfluxDBClient influxDBClient;

    @Autowired
    private PlantDeviceRepository deviceRepository; // <--- 1. INYECCIÓN DE MONGO

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    // --- VALORES POR DEFECTO (FALLBACK) ---
    // Se usan solo si el usuario no ha configurado su planta en MongoDB
    private static final double DEFAULT_MIN_TEMP = 18.0;
    private static final double DEFAULT_MAX_TEMP = 30.0;
    private static final double DEFAULT_MIN_SOIL = 30.0;
    private static final double DEFAULT_MAX_SOIL = 70.0;
    private static final double DEFAULT_MIN_HUMIDITY = 30.0;
    private static final double DEFAULT_MAX_HUMIDITY = 80.0;
    private static final double DEFAULT_MIN_LIGHT = 200.0;
    private static final double DEFAULT_MAX_LIGHT = 5000.0;

    public List<ChartPointDto> getHistoricalData(String plantId, String field, String range) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -%s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                        "|> aggregateWindow(every: 30m, fn: mean, createEmpty: false) " +
                        "|> yield(name: \"mean\")",
                bucket, range,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA,
                InfluxConstants.TAG_PLANT_ID, plantId,
                field);
        return executeQuery(flux);
    }

    /**
     * ✅ AHORA USA LA CONFIGURACIÓN REAL DE MONGODB
     */
    /**
     * ✅ CÁLCULO ESI AVANZADO (Lógica de Penalización Crítica)
     */
    public KpiDto getPlantKpis(String plantId) {
        // 1. Obtener configuración de MongoDB
        PlantDevice deviceConfig = getDeviceConfig(plantId);

        // 2. Definir umbrales (Optimos)
        double minTemp = getSafeConfig(deviceConfig != null ? deviceConfig.getMinTempC() : null, DEFAULT_MIN_TEMP);
        double maxTemp = getSafeConfig(deviceConfig != null ? deviceConfig.getMaxTempC() : null, DEFAULT_MAX_TEMP);

        double minSoil = getSafeConfig(deviceConfig != null ? deviceConfig.getMinSoilHumidity().doubleValue() : null, DEFAULT_MIN_SOIL);
        double maxSoil = getSafeConfig(deviceConfig != null ? deviceConfig.getMaxSoilHumidity().doubleValue() : null, DEFAULT_MAX_SOIL);

        double minLight = getSafeConfig(deviceConfig != null ? deviceConfig.getMinLightLux().doubleValue() : null, DEFAULT_MIN_LIGHT);
        double maxLight = getSafeConfig(deviceConfig != null ? deviceConfig.getMaxLightLux().doubleValue() : null, DEFAULT_MAX_LIGHT); // Default alto

        // Agregamos Humedad Ambiental (Si no existe en config, usamos defaults 40-80)
        double minHumAire = getSafeConfig(deviceConfig != null ? deviceConfig.getMinHumidity().doubleValue() : null, DEFAULT_MIN_HUMIDITY);
        double maxHumAire = getSafeConfig(deviceConfig != null ? deviceConfig.getMaxHumidity().doubleValue() : null, DEFAULT_MAX_HUMIDITY);

        // 3. Obtener valores actuales de InfluxDB
        Double temp = getLastValue(plantId, InfluxConstants.FIELD_TEMPERATURA);
        Double soil = getLastValue(plantId, InfluxConstants.FIELD_HUMEDAD_SUELO);
        Double light = getLastValue(plantId, InfluxConstants.FIELD_LUZ);
        Double humAire = getLastValue(plantId, InfluxConstants.FIELD_HUMEDAD_AIRE);

        // Valores seguros (si sensor es null, asumimos valor medio para no romper la mate, pero bajamos DQR)
        double valTemp = temp != null ? temp : (minTemp + maxTemp) / 2;
        double valSoil = soil != null ? soil : (minSoil + maxSoil) / 2;
        double valLight = light != null ? light : (minLight + maxLight) / 2;
        double valHumAire = humAire != null ? humAire : (minHumAire + maxHumAire) / 2;

        // 4. CALCULO DE PUNTAJES INDIVIDUALES (0.0 a 100.0)
        // Usamos una "tolerancia" relativa. Ej: Temperatura tolera desvios pequeños, Suelo es más estricto.
        double scoreSoil = calculateAdvancedScore(valSoil, minSoil, maxSoil, 15.0); // +/- 15% tolerancia
        double scoreTemp = calculateAdvancedScore(valTemp, minTemp, maxTemp, 10.0); // +/- 10 grados tolerancia
        double scoreHum = calculateAdvancedScore(valHumAire, minHumAire, maxHumAire, 20.0); // +/- 20% tolerancia
        double scoreLight = calculateAdvancedScore(valLight, minLight, maxLight, 500.0); // tolerancia amplia en luz

        // 5. PONDERACIÓN (Weighted Average)
        // Damos más peso al Suelo y Temperatura (Vitales) que a la Luz/Aire (Importantes)
        double weightedEsi = (scoreSoil * 0.40) +
                (scoreTemp * 0.30) +
                (scoreHum * 0.15) +
                (scoreLight * 0.15);

        // 6. FACTOR DE VETO (Critical Penalty)
        // Si el suelo o la temperatura están en 0 (críticos), el ESI total se multiplica por 0.
        // Esto asegura que la planta no tenga "80% de salud" si se está muriendo de sed.
        double penaltyFactor = 1.0;
        if (scoreSoil < 10.0) penaltyFactor *= 0.5; // Castigo severo si el suelo es crítico
        if (scoreTemp < 10.0) penaltyFactor *= 0.5; // Castigo severo si la temperatura es crítica

        double finalEsi = weightedEsi * penaltyFactor;

        // KPI 2: DQR
        double dqr = calculateRealDqr(plantId);

        return new KpiDto(
                temp,
                soil,
                humAire,
                light != null ? light.intValue() : 0,
                Math.round(finalEsi * 10.0) / 10.0, // Redondeo a 1 decimal
                Math.round(dqr * 10.0) / 10.0,
                Instant.now().toString()
        );
    }

    // ==========================================
    // NUEVA MATEMÁTICA DE SALUD (Curva Trapezoidal)
    // ==========================================

    /**
     * Calcula un puntaje de 0 a 100 basado en rangos óptimos y tolerancia.
     * @param val Valor actual del sensor
     * @param min Límite inferior óptimo
     * @param max Límite superior óptimo
     * @param tolerance Cuánto puede salirse del rango antes de llegar a 0 (Zona de Peligro)
     */
    private double calculateAdvancedScore(double val, double min, double max, double tolerance) {
        // Caso 1: Dentro del rango ideal
        if (val >= min && val <= max) {
            return 100.0;
        }

        // Caso 2: Por debajo del mínimo
        if (val < min) {
            double difference = min - val;
            if (difference >= tolerance) return 0.0; // Muerte/Crítico
            // Regla de 3 inversa: Si diferencia es 0 -> 100, Si es tolerance -> 0
            return 100.0 * (1.0 - (difference / tolerance));
        }

        // Caso 3: Por encima del máximo
        else {
            double difference = val - max;
            if (difference >= tolerance) return 0.0;
            return 100.0 * (1.0 - (difference / tolerance));
        }
    }

    // Helper pequeño para evitar nulos en configs
    private double getSafeConfig(Double val, double def) {
        return val != null ? val : def;
    }
    /**
     * ✅ CLUSTERING DINÁMICO
     */
    public ClusterResultDto performClustering(String plantId, String range) {
        // 1. Obtener configuración para saber qué es "Seco" y "Exceso" para ESTA planta
        PlantDevice deviceConfig = getDeviceConfig(plantId);
        double minSoil = deviceConfig != null && deviceConfig.getMinSoilHumidity() != null ? deviceConfig.getMinSoilHumidity() : DEFAULT_MIN_SOIL;
        double maxSoil = deviceConfig != null && deviceConfig.getMaxSoilHumidity() != null ? deviceConfig.getMaxSoilHumidity() : DEFAULT_MAX_SOIL;

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -%s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\")",
                bucket, range,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA,
                InfluxConstants.TAG_PLANT_ID, plantId,
                InfluxConstants.FIELD_HUMEDAD_SUELO);

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        int seco = 0;
        int optimo = 0;
        int exceso = 0;

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Double val = toDouble(record.getValue());
                if (val != null) {
                    // Usamos las variables dinámicas
                    if (val < minSoil) seco++;
                    else if (val > maxSoil) exceso++;
                    else optimo++;
                }
            }
        }
        Map<String, Integer> clusters = new HashMap<>();
        clusters.put("SECO_RIESGO", seco);
        clusters.put("OPTIMO_SALUD", optimo);
        clusters.put("EXCESO_AGUA", exceso);

        return new ClusterResultDto(range, clusters);
    }

    // ==========================================
    // HELPERS
    // ==========================================

    /**
     * Helper para buscar el dispositivo en Mongo de forma segura
     */
    private PlantDevice getDeviceConfig(String plantId) {
        // Asumiendo que tienes un método findByPlantId en tu repositorio
        // Si tu repositorio usa findById, ajusta aquí.
        // Dado que 'plantId' en Influx es el string ID de la planta (ej "Planta123"),
        // buscamos por ese campo en Mongo.
        Optional<PlantDevice> deviceOpt = deviceRepository.findByPlantId(plantId);
        return deviceOpt.orElse(null);
    }

    private double calculateSubScore(double val, double min, double max) {
        if (val >= min && val <= max) return 1.0;
        double tolerance = 15.0; // Podrías parametrizar la tolerancia también si quisieras
        if (val < min) {
            double penalty = (min - val) / tolerance;
            return Math.max(0.0, 1.0 - penalty);
        } else {
            double penalty = (val - max) / tolerance;
            return Math.max(0.0, 1.0 - penalty);
        }
    }

    private double calculateRealDqr(String plantId) {
        String fieldsFilter = String.format(
                "r[\"_field\"] == \"%s\" or r[\"_field\"] == \"%s\" or r[\"_field\"] == \"%s\" or r[\"_field\"] == \"%s\"",
                InfluxConstants.FIELD_TEMPERATURA,
                InfluxConstants.FIELD_HUMEDAD_AIRE,
                InfluxConstants.FIELD_HUMEDAD_SUELO,
                InfluxConstants.FIELD_LUZ
        );

        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -24h) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => %s) " +
                        "|> group() " +
                        "|> count()",
                bucket,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA,
                InfluxConstants.TAG_PLANT_ID, plantId,
                fieldsFilter);

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, org);

            if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
                return 0.0;
            }

            Object val = tables.get(0).getRecords().get(0).getValue();
            Long totalDataPointsReceived = 0L;
            if (val instanceof Long) totalDataPointsReceived = (Long) val;
            else if (val instanceof Integer) totalDataPointsReceived = ((Integer) val).longValue();

            double actualPoints = totalDataPointsReceived.doubleValue();
            double expectedPoints = 288.0 * 4.0;

            double ratio = (actualPoints / expectedPoints) * 100.0;
            return Math.min(100.0, ratio);

        } catch (Exception e) {
            System.err.println("❌ Error calculando DQR: " + e.getMessage());
            return 0.0;
        }
    }

    private List<ChartPointDto> executeQuery(String query) {
        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(query, org);
        List<ChartPointDto> points = new ArrayList<>();
        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Double safeValue = toDouble(record.getValue());
                points.add(new ChartPointDto(
                        record.getTime().toString(),
                        safeValue));
            }
        }
        return points;
    }

    private Double getLastValue(String plantId, String field) {
        String flux = String.format(
                "from(bucket: \"%s\") |> range(start: -24h) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                        "|> last()",
                bucket,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA,
                InfluxConstants.TAG_PLANT_ID, plantId,
                field);
        List<ChartPointDto> res = executeQuery(flux);
        return res.isEmpty() ? null : res.get(0).value();
    }

    private Double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Long) return ((Long) value).doubleValue();
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        return 0.0;
    }
}