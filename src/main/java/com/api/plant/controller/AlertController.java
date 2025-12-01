package com.api.plant.controller;

import com.api.plant.entity.PlantAlert;
import com.api.plant.repository.PlantAlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private PlantAlertRepository alertRepository;

    // 1. Obtener historial de alertas de una planta
    // GET http://localhost:8080/api/alerts/plant/jardin01
    @GetMapping("/plant/{plantId}")
    public ResponseEntity<List<PlantAlert>> getPlantAlerts(@PathVariable String plantId) {
        // Usamos el método que definimos en el repositorio para ordenar por fecha
        List<PlantAlert> alerts = alertRepository.findByPlantIdOrderByTimestampDesc(plantId);

        // Opcional: Limitar a las últimas 50 para no sobrecargar el frontend
        if (alerts.size() > 50) {
            alerts = alerts.subList(0, 50);
        }

        return ResponseEntity.ok(alerts);
    }

    // 2. Marcar una alerta como "Leída" (Para funcionalidad futura del frontend)
    // PUT http://localhost:8080/api/alerts/{alertId}/read
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setRead(true);
                    alertRepository.save(alert);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}