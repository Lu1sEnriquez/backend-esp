package com.api.plant.service;

import com.api.plant.config.InfluxConstants; // <--- IMPORTANTE
import com.api.plant.dto.analytics.DashboardDtos.*;
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

@Service
public class AnalyticsService {

    @Autowired
    private InfluxDBClient influxDBClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String org;

    // --- CONSTANTES BIOLÓGICAS (IDEALES) ---
    private static final double MIN_TEMP_IDEAL = 18.0;
    private static final double MAX_TEMP_IDEAL = 30.0;
    private static final double MIN_SOIL_IDEAL = 40.0;
    private static final double MAX_SOIL_IDEAL = 80.0;
    private static final double MIN_LIGHT_IDEAL = 200.0;

    /**
     * 1. Obtener historial para Gráficas (Line Chart)
     */
    public List<ChartPointDto> getHistoricalData(String plantId, String field, String range) {
        // Usamos Constantes para Measurement y Tag, el 'field' viene dinámico del controlador
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -%s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                        "|> aggregateWindow(every: 30m, fn: mean, createEmpty: false) " +
                        "|> yield(name: \"mean\")",
                bucket, range,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA, // Constante Measurement
                InfluxConstants.TAG_PLANT_ID, plantId,       // Constante Tag
                field);

        return executeQuery(flux);
    }

    /**
     * 2. Calcular KPIs en tiempo real y agregados
     */
    public KpiDto getPlantKpis(String plantId) {
        // A. Obtener últimos valores reportados USANDO CONSTANTES
        Double temp = getLastValue(plantId, InfluxConstants.FIELD_TEMPERATURA);
        Double soil = getLastValue(plantId, InfluxConstants.FIELD_HUMEDAD_SUELO);
        Double light = getLastValue(plantId, InfluxConstants.FIELD_LUZ);

        // Valores seguros
        double safeTemp = temp != null ? temp : 25.0;
        double safeSoil = soil != null ? soil : 50.0;
        double safeLight = light != null ? light : 0.0;

        // B. CÁLCULO DE KPI 1: Índice de Salud (ESI)
        double scoreSoil = calculateSubScore(safeSoil, MIN_SOIL_IDEAL, MAX_SOIL_IDEAL);
        double scoreTemp = calculateSubScore(safeTemp, MIN_TEMP_IDEAL, MAX_TEMP_IDEAL);
        double scoreLight = (safeLight >= MIN_LIGHT_IDEAL) ? 1.0 : 0.5;

        double esi = (scoreSoil * 50.0) + (scoreTemp * 30.0) + (scoreLight * 20.0);

        // C. CÁLCULO DE KPI 2: Calidad de Datos (DQR)
        double dqr = calculateRealDqr(plantId);

        return new KpiDto(
                temp,
                soil,
                light != null ? light.intValue() : 0,
                Math.round(esi * 10.0) / 10.0,
                Math.round(dqr * 10.0) / 10.0,
                Instant.now().toString()
        );
    }

    /**
     * 3. CLUSTERING
     */
    public ClusterResultDto performClustering(String plantId, String range) {
        // Usamos Constantes
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -%s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\")", // Filtramos por Humedad Suelo
                bucket, range,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA,
                InfluxConstants.TAG_PLANT_ID, plantId,
                InfluxConstants.FIELD_HUMEDAD_SUELO); // Constante Campo

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        int seco = 0;
        int optimo = 0;
        int exceso = 0;

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                Double val = toDouble(record.getValue());
                if (val != null) {
                    if (val < MIN_SOIL_IDEAL) seco++;
                    else if (val > MAX_SOIL_IDEAL) exceso++;
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
    // MÉTODOS PRIVADOS DE LÓGICA MATEMÁTICA
    // ==========================================

    private double calculateSubScore(double val, double min, double max) {
        if (val >= min && val <= max) return 1.0;
        double tolerance = 15.0;
        if (val < min) {
            double penalty = (min - val) / tolerance;
            return Math.max(0.0, 1.0 - penalty);
        } else {
            double penalty = (val - max) / tolerance;
            return Math.max(0.0, 1.0 - penalty);
        }
    }

    /**
     * Calcula el DQR contando datos de temperatura usando Constantes
     */
    private double calculateRealDqr(String plantId) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -24h) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"%s\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                        "|> group() " +
                        "|> count()",
                bucket,
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA, // Constante Measurement
                InfluxConstants.TAG_PLANT_ID, plantId,       // Constante Tag
                InfluxConstants.FIELD_TEMPERATURA);          // Constante Field

        try {
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(flux, org);

            if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
                // Debug opcional
                // System.out.println("⚠️ DQR 0% - No data found for: " + plantId);
                return 0.0;
            }

            Object val = tables.get(0).getRecords().get(0).getValue();
            Long count = 0L;
            if (val instanceof Long) count = (Long) val;
            else if (val instanceof Integer) count = ((Integer) val).longValue();

            double actualPoints = count.doubleValue();
            double expectedPoints = 288.0; // 1 dato cada 5 min en 24h

            double ratio = (actualPoints / expectedPoints) * 100.0;
            return Math.min(100.0, ratio);

        } catch (Exception e) {
            System.err.println("❌ Error calculando DQR: " + e.getMessage());
            return 0.0;
        }
    }

    // ==========================================
    // HELPERS DE INFLUXDB
    // ==========================================

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
                InfluxConstants.MEASUREMENT_SENSORES_PLANTA, // Constante Measurement
                InfluxConstants.TAG_PLANT_ID, plantId,       // Constante Tag
                field); // Campo dinámico (pero pasado con constante desde getPlantKpis)

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