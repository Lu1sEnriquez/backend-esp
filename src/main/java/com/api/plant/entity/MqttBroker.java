package com.api.plant.entity;



import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entidad para el Servicio de Descubrimiento. Permite a Spring conectarse dinámicamente al broker.
 */
@Document(collection = "mqtt_brokers")
public class MqttBroker {

    @Id
    private String id;
    private String name;
    private String url; // URL del broker (ej. 0.tcp.ngrok.io)
    private Integer port;
    private Boolean isActive; // Bandera para que el Discovery Service sepa a cuál conectarse

    public MqttBroker() {

    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }
}