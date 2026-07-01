package com.gestorpyme.controller;

import com.gestorpyme.service.AuthService;
import com.gestorpyme.service.ResultadoAutenticacion;

/**
 * Controlador del inicio de sesion (patron MVC/MVP simplificado, solo en la capa de interfaz).
 * Recibe los datos capturados por la vista, delega la logica en {@link AuthService} y
 * devuelve el resultado. No contiene reglas de negocio, ni SQL, ni componentes Swing.
 * Capa: controller.
 */
public class LoginController {

    private final AuthService authService;

    /** Constructor por defecto: crea su propio servicio. */
    public LoginController() {
        this(new AuthService());
    }

    /** Constructor que permite inyectar el servicio (util en pruebas). */
    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Procesa un intento de inicio de sesion.
     *
     * @param nombreUsuario nombre de usuario capturado en la vista.
     * @param passwordPlano contrasena capturada en la vista.
     * @return el {@link ResultadoAutenticacion} para que la vista lo muestre.
     */
    public ResultadoAutenticacion autenticar(String nombreUsuario, String passwordPlano) {
        return authService.autenticar(nombreUsuario, passwordPlano);
    }
}
