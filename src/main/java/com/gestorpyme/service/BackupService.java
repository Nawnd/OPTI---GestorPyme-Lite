package com.gestorpyme.service;

import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Respaldo y restauración de la base de datos local SQLite (Paso T).
 *
 * Capa: service. Responsabilidad: copiar de forma segura el archivo de base de datos
 * (`data/gestorpyme.db`) hacia/desde una ruta elegida por el usuario, con validaciones.
 * Qué NO debe hacer: no ejecuta SQL, no toca el esquema, no conoce Swing. La confirmación
 * con el usuario es responsabilidad de la vista; aquí solo se valida y se copia.
 * Riesgos: la restauración reemplaza el archivo en uso; se recomienda reiniciar la aplicación
 * después de restaurar (la vista informa de ello). La ruta de la base es la única fuente:
 * {@link DatabaseConnection#getDatabasePath()}.
 */
public class BackupService {

    /** Sello de tiempo para el nombre sugerido del respaldo. */
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** Ruta del archivo de base de datos actual (origen del respaldo / destino de la restauración). */
    public Path rutaBaseDatos() {
        return DatabaseConnection.getDatabasePath();
    }

    /** Nombre de archivo sugerido para un respaldo nuevo: {@code gestorpyme_backup_yyyyMMdd_HHmmss.db}. */
    public String nombreSugerido() {
        return "gestorpyme_backup_" + LocalDateTime.now().format(SELLO) + ".db";
    }

    /**
     * Crea un respaldo copiando la base de datos actual al destino indicado.
     *
     * @param destino ruta del archivo de respaldo a generar (no nula).
     * @return la ruta del respaldo creado.
     * @throws IOException si la base actual no existe, si el destino es la misma base en uso,
     *                     o si falla la copia.
     */
    public Path crearRespaldo(Path destino) throws IOException {
        if (destino == null) {
            throw new IllegalArgumentException("La ruta de destino del respaldo es obligatoria.");
        }
        Path origen = rutaBaseDatos();
        if (!Files.exists(origen)) {
            throw new IOException("La base de datos actual no existe: " + origen);
        }
        // Evita respaldar sobre el mismo archivo en uso (no tendría sentido y arriesga corrupción).
        if (mismaRuta(origen, destino)) {
            throw new IOException("El destino no puede ser la misma base de datos en uso.");
        }
        crearDirectorioPadre(destino);
        Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        return destino;
    }

    /**
     * Restaura la base de datos reemplazando el archivo actual con el respaldo indicado.
     * La vista debe confirmar con el usuario antes de llamar a este método y recomendar reiniciar.
     *
     * @param origen ruta del archivo de respaldo a restaurar (debe existir y ser un archivo regular).
     * @return la ruta de la base de datos restaurada.
     * @throws IOException si el respaldo no existe/!es válido, si coincide con la base actual,
     *                     o si falla la copia.
     */
    public Path restaurarRespaldo(Path origen) throws IOException {
        if (origen == null) {
            throw new IllegalArgumentException("La ruta del respaldo a restaurar es obligatoria.");
        }
        if (!Files.exists(origen) || !Files.isRegularFile(origen)) {
            throw new IOException("El archivo de respaldo no existe o no es válido: " + origen);
        }
        Path destino = rutaBaseDatos();
        // Restaurar la propia base sobre sí misma no aporta nada y podría confundir.
        if (mismaRuta(origen, destino)) {
            throw new IOException("El respaldo seleccionado es la base actual; no hay nada que restaurar.");
        }
        crearDirectorioPadre(destino);
        Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        return destino;
    }

    /** Crea el directorio contenedor del destino si aún no existe (evita fallos de copia). */
    private static void crearDirectorioPadre(Path destino) throws IOException {
        Path padre = destino.toAbsolutePath().getParent();
        if (padre != null) {
            Files.createDirectories(padre);
        }
    }

    /**
     * Indica si dos rutas apuntan al mismo archivo. Usa {@code isSameFile} cuando ambos existen
     * (más fiable ante enlaces/rutas equivalentes) y, si no, compara rutas absolutas normalizadas.
     */
    private static boolean mismaRuta(Path a, Path b) {
        try {
            if (Files.exists(a) && Files.exists(b)) {
                return Files.isSameFile(a, b);
            }
        } catch (IOException ignorado) {
            // Si la comparación por sistema de archivos falla, se cae al comparativo textual.
        }
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
