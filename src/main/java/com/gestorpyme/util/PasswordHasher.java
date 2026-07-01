package com.gestorpyme.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utilidad para el manejo seguro de contrasenas mediante BCrypt (libreria jBCrypt).
 * Las contrasenas NUNCA se guardan en texto plano: solo se almacena el hash.
 * Clase de utilidad sin estado (metodos estaticos).
 */
public final class PasswordHasher {

    /**
     * Factor de costo (work factor) de BCrypt. A mayor valor, mas lento y mas seguro.
     * 12 ofrece un buen equilibrio para una aplicacion de escritorio.
     */
    private static final int COSTO = 12;

    private PasswordHasher() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Genera el hash BCrypt de una contrasena en texto plano.
     * Cada hash incluye su propio "salt" aleatorio, por lo que dos hashes de la
     * misma contrasena seran distintos (comportamiento esperado y seguro).
     *
     * @param passwordPlano contrasena en texto plano (no nula ni vacia).
     * @return hash BCrypt (cadena que empieza por "$2a$").
     * @throws IllegalArgumentException si la contrasena es nula o vacia.
     */
    public static String hash(String passwordPlano) {
        if (passwordPlano == null || passwordPlano.isEmpty()) {
            throw new IllegalArgumentException("La contrasena no puede ser nula o vacia");
        }
        return BCrypt.hashpw(passwordPlano, BCrypt.gensalt(COSTO));
    }

    /**
     * Verifica si una contrasena en texto plano corresponde a un hash BCrypt.
     * Si el hash es nulo, vacio o no tiene formato BCrypt valido (p. ej. un valor
     * provisional), devuelve false en lugar de lanzar excepcion: asi un inicio de
     * sesion sobre una cuenta sin contrasena valida falla de forma segura.
     *
     * @param passwordPlano contrasena en texto plano a comprobar.
     * @param hashAlmacenado hash previamente generado con {@link #hash(String)}.
     * @return true si coinciden; false en cualquier otro caso.
     */
    public static boolean verify(String passwordPlano, String hashAlmacenado) {
        if (passwordPlano == null || hashAlmacenado == null || hashAlmacenado.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(passwordPlano, hashAlmacenado);
        } catch (IllegalArgumentException e) {
            // El hash almacenado no tiene formato BCrypt valido.
            return false;
        }
    }
}
