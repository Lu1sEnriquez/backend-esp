package com.api.plant.config;


public class InfluxConstants {

    // Measurement names
    public static final String MEASUREMENT_SENSORES_PLANTA = "sensores_planta";

    // Field names
    public static final String FIELD_TEMPERATURA = "temperatura";
    public static final String FIELD_HUMEDAD_AIRE = "humedad_aire";
    public static final String FIELD_HUMEDAD_SUELO = "humedad_suelo";
    public static final String FIELD_LUZ = "luz";
    public static final String FIELD_BOMBA_ESTADO = "bomba_estado";
    public static final String FIELD_ALERTA_ACTIVA = "alerta_activa";

    // Tag names
    public static final String TAG_PLANT_ID = "plantId";
    public static final String TAG_STATUS_QC = "status_qc";

    private InfluxConstants() {
        // Clase de constantes, no instanciable
    }
}