package com.api.plant.service;

import com.api.plant.entity.Reading;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat; // Importante
import java.util.Date;
import java.util.TimeZone; // Opcional si quieres forzar zona horaria

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarAlertaHtml(String para, String asunto, String mensaje,
                                 String nivel, String plantId, Reading lectura) {
        try {
            // --- 1. LÓGICA DE FECHA CORREGIDA (Instant -> Date) ---
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            // CORRECCIÓN AQUÍ: Convertimos de Instant a Date
            Date fechaObjeto = null;

            if (lectura != null && lectura.getTimestamp() != null) {
                // Usamos Date.from() para convertir el Instant
                fechaObjeto = Date.from(lectura.getTimestamp());
            }

            // Validación de fecha nula o fecha 1970 (error de sensor)
            // 100000000000L milisegundos equivale aprox al año 1973.
            if (fechaObjeto == null || fechaObjeto.getTime() < 100000000000L) {
                fechaObjeto = new Date(); // Usar fecha actual del sistema
            }

            String fecha = sdf.format(fechaObjeto);
            // -----------------------------------------------------

            String humSuelo = (lectura != null) ? String.valueOf(lectura.getSoilHumidity()) : "N/A";
            String humAmb = (lectura != null) ? String.valueOf(lectura.getAmbientHumidity()) : "N/A";
            String temp = (lectura != null) ? String.valueOf(lectura.getTempC()) : "N/A";
            String luz = (lectura != null) ? String.valueOf(lectura.getLightLux()) : "N/A";

            // 2. Definimos el color según el nivel
            String colorFondo = "#5bc0de"; // Default INFO (Azul)
            if ("CRITICO".equalsIgnoreCase(nivel)) colorFondo = "#d9534f"; // Rojo
            else if ("ADVERTENCIA".equalsIgnoreCase(nivel)) colorFondo = "#f0ad4e"; // Naranja

            // 3. HTML String Block
            String htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; color: #333; }
                    .container { padding: 20px; border: 1px solid #ddd; border-radius: 8px; max-width: 600px; margin: 0 auto; }
                    .alert { padding: 10px; border-radius: 4px; color: white; text-align: center; font-weight: bold; background-color: %s; }
                    .details { margin-top: 15px; }
                    ul { list-style-type: none; padding: 0; }
                    li { margin-bottom: 5px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="alert">
                        <h2>%s</h2>
                    </div>
                    
                    <p>Hola,</p>
                    <p>%s</p>
                    
                    <div class="details">
                        <h3>Detalles de la Lectura:</h3>
                        <ul>
                            <li><strong>ID Planta:</strong> %s</li>
                            <li><strong>Humedad Suelo:</strong> %s%%</li>
                            <li><strong>Humedad Ambiental:</strong> %s%%</li>
                            <li><strong>Temperatura:</strong> %s°C</li>
                            <li><strong>Luz:</strong> %s lux</li>
                            <li><strong>Fecha:</strong> %s</li>
                        </ul>
                    </div>
                    
                    <p style="font-size: 12px; color: #777; margin-top: 20px;">
                        Este es un mensaje automático del sistema de monitoreo IoT.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(colorFondo, asunto, mensaje, plantId, humSuelo, humAmb, temp, luz, fecha);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(para);
            helper.setSubject(asunto);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            System.out.println("📧 Correo HTML enviado a: " + para);

        } catch (MessagingException e) {
            System.err.println("❌ Error al enviar correo HTML: " + e.getMessage());
        }
    }

    public void enviarCorreo(String para, String asunto, String cuerpo) {
        try {
            org.springframework.mail.SimpleMailMessage mensaje = new org.springframework.mail.SimpleMailMessage();
            mensaje.setTo(para);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            mailSender.send(mensaje);
            System.out.println("📧 Correo simple enviado a: " + para);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar correo simple: " + e.getMessage());
        }
    }
}