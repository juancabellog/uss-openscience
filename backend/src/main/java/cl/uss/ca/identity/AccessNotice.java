package cl.uss.ca.identity;

/** Aviso de acceso. No lleva contraseña: Ethics no tiene un segundo secreto. */
public record AccessNotice(String email, String template, String at) {
}
