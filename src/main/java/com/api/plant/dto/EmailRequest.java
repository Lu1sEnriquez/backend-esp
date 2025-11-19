package com.api.plant.dto;


/**
 * DTO (Data Transfer Object) que representa la solicitud para enviar un correo.
 * Usamos un 'record' de Java para una definición inmutable y concisa.
 *
 * @param destinatario La dirección de correo del destinatario.
 * @param asunto El título o asunto del correo.
 * @param mensaje El cuerpo o contenido del correo.
 */
public record EmailRequest(String destinatario, String asunto, String mensaje) {
}