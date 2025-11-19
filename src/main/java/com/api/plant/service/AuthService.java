package com.api.plant.service;

import com.api.plant.entity.AppUser;
import com.api.plant.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Servicio encargado de la lógica de negocio de autenticación, como el registro de usuarios.
 */
@Service
public class AuthService {

    @Autowired
    private AppUserRepository userRepository;

    // Se inyecta el Bean BCryptPasswordEncoder definido en SecurityConfig
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Registra un nuevo usuario en el sistema.
     * Cifra la contraseña antes de la persistencia.
     * * @param username El nombre de usuario.
     * @param rawPassword La contraseña en texto plano recibida del frontend.
     * @return El objeto AppUser guardado.
     */
    public AppUser registerNewUser(String username, String rawPassword) {

        // 1. Crear la entidad
        AppUser newUser = new AppUser();
        newUser.setUsername(username);

        // 2. Cifrar la contraseña ANTES de guardarla (¡PASO CRUCIAL!)
        // BCryptPasswordEncoder genera un hash seguro a partir del texto plano.
        String hashedPassword = passwordEncoder.encode(rawPassword);
        newUser.setPasswordHash(hashedPassword);

        // 3. Inicializar la lista de plantas (vacía al principio)
        newUser.setPlantsIds(new ArrayList<>());

        // 4. Se asigna un email (si se recibe en el DTO de registro)
        // (Asumiendo que el DTO de entrada maneja el email)
        // Por simplicidad, aquí no lo asignamos, pero es el punto de la lógica.

        // 5. Guardar el nuevo AppUser cifrado en MongoDB
        return userRepository.save(newUser);
    }

    // NOTA: La lógica de 'login' real la maneja Spring Security (UserDetailsServiceImpl)
    // comparando la contraseña raw con el passwordHash usando el mismo PasswordEncoder.
}