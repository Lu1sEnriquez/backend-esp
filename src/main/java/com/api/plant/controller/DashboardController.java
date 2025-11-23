package com.api.plant.controller;

import com.api.plant.dto.analytics.DashboardDtos.*;
import com.api.plant.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
// @CrossOrigin(origins = "http://localhost:3000") // Descomentar si Next.js te da error de CORS
public class DashboardController {

    @Autowired
    private AnalyticsService analyticsService;

    /**
     * GET /api/analytics/{plantId}/kpi
     * Retorna: Estado actual, Indice de Salud (ESI) y Calidad (DQR)
     */
    @GetMapping("/{plantId}/kpi")
    public ResponseEntity<KpiDto> getPlantKPIs(@PathVariable String plantId) {
        return ResponseEntity.ok(analyticsService.getPlantKpis(plantId));
    }

    /**
     * GET /api/analytics/{plantId}/history?field=temperatura&range=24h
     * Retorna: Array de puntos [time, value] para graficar.
     */
    @GetMapping("/{plantId}/history")
    public ResponseEntity<List<ChartPointDto>> getHistory(
            @PathVariable String plantId,
            @RequestParam(defaultValue = "temperatura") String field,
            @RequestParam(defaultValue = "24h") String range
    ) {
        return ResponseEntity.ok(analyticsService.getHistoricalData(plantId, field, range));
    }

    /**
     * GET /api/analytics/{plantId}/clustering?range=7d
     * Retorna: Agrupamiento de datos para Pie Chart (Requisito Unidad 3)
     */
    @GetMapping("/{plantId}/clustering")
    public ResponseEntity<ClusterResultDto> getClustering(
            @PathVariable String plantId,
            @RequestParam(defaultValue = "7d") String range
    ) {
        return ResponseEntity.ok(analyticsService.performClustering(plantId, range));
    }
}