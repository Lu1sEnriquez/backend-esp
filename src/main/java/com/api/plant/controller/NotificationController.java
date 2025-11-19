package com.api.plant.controller;


import com.api.plant.dto.EmailRequest;
import com.api.plant.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificaciones") // Ruta base para este controlador
public class NotificationController {

    @Autowired
    private EmailService emailService;

    @PostMapping("/enviar-correo")
    public ResponseEntity<String> enviarCorreo(@RequestBody EmailRequest emailRequest) {
        emailService.enviarCorreo(
                emailRequest.destinatario(),
                emailRequest.asunto(),
                emailRequest.mensaje()
        );
        return ResponseEntity.ok("Solicitud de envío de correo recibida correctamente.");
    }
}