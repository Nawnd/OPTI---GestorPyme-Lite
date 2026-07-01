package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.service.ExportacionService;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Controlador del modulo Exportacion. Coordina la vista con {@link ExportacionService}.
 * Capa: controller.
 */
public class ExportacionController {

    private final ExportacionService exportacionService;

    public ExportacionController() {
        this(new ExportacionService());
    }

    public ExportacionController(ExportacionService exportacionService) {
        this.exportacionService = exportacionService;
    }

    public void exportar(TipoExportacion tipo, String rutaArchivo, Integer idUsuario)
            throws SQLException, IOException {
        exportacionService.exportar(tipo, rutaArchivo, idUsuario);
    }
}
