package com.api.plant.service;

import com.api.plant.entity.AppUser;
import com.api.plant.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ... (Tu método registerNewUser se queda igual) ...
    public AppUser registerNewUser(String username, String rawPassword, String email) {
        AppUser newUser = new AppUser();
        newUser.setUsername(username);
        newUser.setEmail(email);
        String hashedPassword = passwordEncoder.encode(rawPassword);
        newUser.setPasswordHash(hashedPassword);
        newUser.setPlantsIds(new ArrayList<>());
        return userRepository.save(newUser);
    }

    /**
     * ✅ NUEVO METODO: Busca un usuario por su nombre de usuario.
     * Útil para devolver los datos completos después de un login exitoso.
     */
    public Optional<AppUser> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
}