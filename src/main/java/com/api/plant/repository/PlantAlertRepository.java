package com.api.plant.repository;

import com.api.plant.entity.PlantAlert;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PlantAlertRepository extends MongoRepository<PlantAlert, String> {
    // Buscar alertas de una planta ordenadas por fecha reciente
    List<PlantAlert> findByPlantIdOrderByTimestampDesc(String plantId);

    // Buscar solo las no leídas
    List<PlantAlert> findByPlantIdAndIsReadFalse(String plantId);
}