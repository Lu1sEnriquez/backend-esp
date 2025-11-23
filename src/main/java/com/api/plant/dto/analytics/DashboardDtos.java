
package com.api.plant.dto.analytics;

import java.util.Map;

// Clase contenedora para mantener orden
public class DashboardDtos {

    // 1. Para graficar líneas en el tiempo (Chart.js / Recharts)
    public record ChartPointDto(String time, Double value) {}

    // 2. Los KPIs calculados
    public record KpiDto(
            Double currentTemp,
            Double currentSoil,
            Integer currentLight,
            Double healthIndex,   // ESI (KPI 1)
            Double dataQuality,   // DQR (KPI 2)
            String lastUpdate
    ) {}

    // 3. Resultado del Clustering (Requisito Unidad 3)
    public record ClusterResultDto(
            String period, // "24h", "7d"
            Map<String, Integer> clusters // Ej: {"SECO": 10, "OPTIMO": 50, "SATURADO": 5}
    ) {}
}