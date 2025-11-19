package com.api.plant.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

/**
 * Entidad que representa al usuario de la aplicación para Login y control de acceso.
 */
@Document(collection = "app_users")
public class AppUser {

    @Id
    private String id; // ID único de MongoDB

    // Credenciales de acceso
    private String username;
    private String passwordHash; // Almacena la contraseña cifrada (BCrypt)
    private String email;

    // IDs de las plantas que este usuario gestiona (Relación 1:N con PlantDevice)
    private List<String> plantsIds;

    public AppUser() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<String> getPlantsIds() {
        return plantsIds;
    }

    public void setPlantsIds(List<String> plantsIds) {
        this.plantsIds = plantsIds;
    }
}