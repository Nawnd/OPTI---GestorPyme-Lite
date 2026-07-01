package com.gestorpyme.controller;

import com.gestorpyme.service.BackupService;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Controlador delgado de respaldo/restauración de la base de datos (Paso T).
 *
 * Capa: controller. Responsabilidad: coordinar la vista de Configuración con {@link BackupService}.
 * Qué NO debe hacer: no contiene lógica de archivos ni de negocio (solo delega).
 */
public class BackupController {

    private final BackupService service;

    /** Construcción por defecto con el servicio estándar. */
    public BackupController() {
        this(new BackupService());
    }

    /** Permite inyectar el servicio (útil para pruebas). */
    public BackupController(BackupService service) {
        this.service = service;
    }

    /** Nombre de archivo sugerido para un respaldo nuevo. */
    public String nombreRespaldoSugerido() {
        return service.nombreSugerido();
    }

    /** Ruta del archivo de base de datos actual. */
    public Path rutaBaseActual() {
        return service.rutaBaseDatos();
    }

    /** Crea un respaldo de la base actual en el destino indicado. */
    public Path crearRespaldo(Path destino) throws IOException {
        return service.crearRespaldo(destino);
    }

    /** Restaura la base de datos desde el respaldo indicado. */
    public Path restaurarRespaldo(Path origen) throws IOException {
        return service.restaurarRespaldo(origen);
    }
}
