package com.api.plant.dto;

/**
 * Data Transfer Object (DTO) para mapear el payload JSON entrante del ESP32.
 */
public class MqttReadingDto {

    // Se recomienda usar el mismo nombre del campo JSON (snake_case)
    private String plant_id;
    private Long timestamp;
    private Double temp_c;
    private Integer humidity_p;
    private Integer light_lux;


    public MqttReadingDto() {
    }

    public String getPlant_id() {
        return plant_id;
    }

    public void setPlant_id(String plant_id) {
        this.plant_id = plant_id;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Double getTemp_c() {
        return temp_c;
    }

    public void setTemp_c(Double temp_c) {
        this.temp_c = temp_c;
    }

    public Integer getHumidity_p() {
        return humidity_p;
    }

    public void setHumidity_p(Integer humidity_p) {
        this.humidity_p = humidity_p;
    }

    public Integer getLight_lux() {
        return light_lux;
    }

    public void setLight_lux(Integer light_lux) {
        this.light_lux = light_lux;
    }
}