package com.api.plant.repository;

import com.api.plant.entity.Reading;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReadingRepository extends MongoRepository<Reading, String> {


    // Fragmento de ReadingRepository
    Optional<Reading> findTopByPlantIdAndQcStatusOrderByTimestampDesc(
            @Param("plantId") String plantId,
            @Param("qcStatus") Reading.QcStatus qcStatus
    );
}