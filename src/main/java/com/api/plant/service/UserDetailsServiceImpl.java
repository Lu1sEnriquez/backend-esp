package com.api.plant.service;

import com.api.plant.entity.AppUser;
import com.api.plant.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Busca al usuario en la BD de Mongo.
        Optional<AppUser> appUser = userRepository.findByUsername(username);

        if (appUser.isEmpty()) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }

        // Mapea la entidad AppUser a la interfaz UserDetails.
        return new org.springframework.security.core.userdetails.User(
                appUser.get().getUsername(),
                appUser.get().getPasswordHash(), // Importante: Usa el hash cifrado
                Collections.emptyList() // Colección vacía de authorities (roles)
        );
    }
}