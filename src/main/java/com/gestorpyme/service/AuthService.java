package com.gestorpyme.service;

import com.gestorpyme.domain.model.Usuario;
import com.gestorpyme.repository.UsuarioRepository;
import com.gestorpyme.util.PasswordHasher;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Logica de negocio de autenticacion (inicio de sesion).
 * Coordina el repositorio de usuarios y la verificacion de contrasena.
 * No contiene SQL (eso es del repositorio) ni codigo de interfaz (eso es de la vista).
 * Capa: service.
 */
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    /** Constructor por defecto: crea su propio repositorio. */
    public AuthService() {
        this(new UsuarioRepository());
    }

    /** Constructor que permite inyectar el repositorio (util en pruebas). */
    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Intenta autenticar a un usuario aplicando estas reglas:
     *  - Usuario o contrasena vacios -&gt; CREDENCIALES_INVALIDAS.
     *  - Usuario inexistente o contrasena incorrecta -&gt; CREDENCIALES_INVALIDAS
     *    (mismo resultado para no revelar si el usuario existe).
     *  - Usuario correcto pero INACTIVO -&gt; USUARIO_INACTIVO.
     *  - Todo correcto -&gt; EXITO (incluye el usuario).
     *  - Error de base de datos -&gt; ERROR.
     *
     * @param nombreUsuario nombre de usuario.
     * @param passwordPlano contrasena en texto plano.
     * @return el {@link ResultadoAutenticacion} correspondiente.
     */
    public ResultadoAutenticacion autenticar(String nombreUsuario, String passwordPlano) {
        // 1) Validacion basica de entradas.
        if (nombreUsuario == null || nombreUsuario.trim().isEmpty()
                || passwordPlano == null || passwordPlano.isEmpty()) {
            return ResultadoAutenticacion.credencialesInvalidas();
        }

        try {
            // 2) Buscar el usuario.
            Optional<Usuario> encontrado =
                    usuarioRepository.buscarPorNombreUsuario(nombreUsuario.trim());
            if (encontrado.isEmpty()) {
                return ResultadoAutenticacion.credencialesInvalidas();
            }

            Usuario usuario = encontrado.get();

            // 3) Verificar la contrasena contra el hash almacenado.
            if (!PasswordHasher.verify(passwordPlano, usuario.getPasswordHash())) {
                return ResultadoAutenticacion.credencialesInvalidas();
            }

            // 4) Verificar que la cuenta este activa.
            if (!usuario.estaActivo()) {
                return ResultadoAutenticacion.usuarioInactivo();
            }

            // 5) Autenticacion correcta.
            return ResultadoAutenticacion.exito(usuario);

        } catch (SQLException e) {
            System.err.println("Error de base de datos durante la autenticacion: " + e.getMessage());
            return ResultadoAutenticacion.error("No se pudo acceder a la base de datos");
        }
    }
}
