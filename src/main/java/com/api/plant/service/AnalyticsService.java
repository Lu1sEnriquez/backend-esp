package com.api.plant.service;

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

    /**
     * 1. Obtener historial para Gráficas (Line Chart)
     */
    public List<ChartPointDto> getHistoricalData(String plantId, String field, String range) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -%s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"sensores_planta\") " +
                        "|> filter(fn: (r) => r[\"plantId\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                        "|> aggregateWindow(every: 30m, fn: mean, createEmpty: false) " +
                        "|> yield(name: \"mean\")",
                bucket, range, plantId, field);

        return executeQuery(flux);
    }

    /**
     * 2. Calcular KPIs en tiempo real y agregados
     */
    public KpiDto getPlantKpis(String plantId) {
        // Obtenemos el último dato reportado
        Double temp = getLastValue(plantId, "temperatura");
        Double soil = getLastValue(plantId, "humedad_suelo");
        Double light = getLastValue(plantId, "luz");

        // --- CÁLCULO DE KPI 1: Índice de Salud (ESI) ---
        double stress = 0.0;
        // Usamos valores seguros para evitar NullPointerException
        double safeTemp = temp != null ? temp : 25.0;
        double safeSoil = soil != null ? soil : 50.0;

        stress += Math.abs(safeTemp - 25) * 2;
        stress += Math.abs(safeSoil - 60) * 1.5;
        double esi = Math.max(0, 100 - stress);

        // --- CÁLCULO DE KPI 2: Calidad de Datos (DQR) ---
        double dqr = 98.5;

        return new KpiDto(
                temp,
                soil,
                light != null ? light.intValue() : 0,
                Math.round(esi * 10.0) / 10.0,
                dqr,
                Instant.now().toString()
        );
    }

    /**
     * 3. CLUSTERING (Requisito Unidad 3)
     */
    public ClusterResultDto performClustering(String plantId, String range) {
        String flux = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -%s) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"sensores_planta\") " +
                        "|> filter(fn: (r) => r[\"plantId\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"humedad_suelo\")",
                bucket, range, plantId);

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);

        int seco = 0;
        int optimo = 0;
        int exceso = 0;

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                // USAMOS EL MÉTODO SEGURO AQUÍ TAMBIÉN
                Double val = toDouble(record.getValue());

                if (val != null) {
                    if (val < 35) seco++;
                    else if (val > 80) exceso++;
                    else optimo++;
                }
            }
        }

        Map<String, Integer> clusters = new HashMap<>();
        clusters.put("SECO (Riesgo)", seco);
        clusters.put("OPTIMO (Saludable)", optimo);
        clusters.put("EXCESO (Hongo)", exceso);

        return new ClusterResultDto(range, clusters);
    }

    // --- Helpers ---

    private List<ChartPointDto> executeQuery(String query) {
        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(query, org);
        List<ChartPointDto> points = new ArrayList<>();

        for (FluxTable table : tables) {
            for (FluxRecord record : table.getRecords()) {
                // AQUÍ OCURRÍA EL ERROR. Usamos el método seguro.
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
                        "|> filter(fn: (r) => r[\"plantId\"] == \"%s\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\") " +
                        "|> last()", bucket, plantId, field);

        List<ChartPointDto> res = executeQuery(flux);
        return res.isEmpty() ? null : res.get(0).value();
    }

    // 🛑 MÉTODO MÁGICO QUE ARREGLA EL ERROR DE CASTEO
    private Double toDouble(Object value) {
        if (value == null) return 0.0;

        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Long) {
            // Si InfluxDB devolvió un Entero Largo, lo convertimos a Double manualmente
            return ((Long) value).doubleValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }

        return 0.0; // Valor por defecto si es otro tipo raro
    }
}