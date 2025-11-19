package com.api.plant.repository;

import com.api.plant.entity.AppUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

/**
 * Repositorio para la entidad AppUser.
 * Extiende MongoRepository para obtener operaciones CRUD básicas.
 */
public interface AppUserRepository extends MongoRepository<AppUser, String> {

    /**
     * Método crucial para la autenticación: busca un usuario por su nombre de usuario.
     * Spring Data genera la consulta a MongoDB automáticamente.
     */
    Optional<AppUser> findByUsername(String username);

    // El AppUser tiene un ID de MongoDB (String) como su clave primaria.
}