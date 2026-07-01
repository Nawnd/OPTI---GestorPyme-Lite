package com.gestorpyme.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la utilidad de hashing de contrasenas.
 */
class PasswordHasherTest {

    @Test
    void hashEsVerificable() {
        String hash = PasswordHasher.hash("Secreta123");
        assertTrue(PasswordHasher.verify("Secreta123", hash),
                "La contrasena correcta deberia verificarse");
    }

    @Test
    void contrasenaIncorrectaNoVerifica() {
        String hash = PasswordHasher.hash("Secreta123");
        assertFalse(PasswordHasher.verify("otra", hash),
                "Una contrasena incorrecta no deberia verificarse");
    }

    @Test
    void dosHashesDeLaMismaClaveSonDistintos() {
        // BCrypt usa un salt aleatorio por hash.
        assertNotEquals(PasswordHasher.hash("igual"), PasswordHasher.hash("igual"),
                "Dos hashes de la misma clave deberian diferir por el salt");
    }

    @Test
    void hashInvalidoDevuelveFalseSinExcepcion() {
        assertFalse(PasswordHasher.verify("algo", "no-es-un-hash-bcrypt"),
                "Un hash invalido deberia devolver false");
    }
}
