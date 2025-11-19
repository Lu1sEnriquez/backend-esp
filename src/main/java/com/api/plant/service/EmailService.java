package com.api.plant.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    // Spring inyectará automáticamente la configuración que pusimos en application.properties
    @Autowired
    private JavaMailSender mailSender;

    /**
     * Envía un correo electrónico simple.
     *
     * @param para El destinatario del correo.
     * @param asunto El asunto del correo.
     * @param cuerpo El contenido del mensaje.
     */
    public void enviarCorreo(String para, String asunto, String cuerpo) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(para);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            // El remitente se toma del 'username' en application.properties

            mailSender.send(mensaje);
            System.out.println("Correo enviado exitosamente a: " + para);
        } catch (Exception e) {
            System.err.println("Error al enviar el correo: " + e.getMessage());
            // Aquí podrías lanzar una excepción personalizada o manejar el error
        }
    }
}