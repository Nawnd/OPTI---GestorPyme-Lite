package com.gestorpyme.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * Conversion centralizada de fechas entre la VISTA y la BASE DE DATOS.
 *
 * - Vista (lo que ve y escribe el usuario): formato {@code dd/MM/yyyy} (ej. 15/06/2026).
 * - Logica / base de datos: formato ISO {@code yyyy-MM-dd}, compatible con SQLite y LocalDate.
 *
 * El parseo es ESTRICTO (ResolverStyle.STRICT): rechaza fechas inexistentes como 31/02/2026.
 * No depende de Swing, por lo que es facilmente comprobable con pruebas unitarias.
 *
 * Clase de utilidades: no se instancia.
 */
public final class DateFormatter {

    /** Formato visible para el usuario. */
    public static final String PATRON_VISTA = "dd/MM/uuuu";
    /** Formato de almacenamiento (ISO, el que usa SQLite y LocalDate). */
    public static final String PATRON_ISO = "uuuu-MM-dd";

    // Se usa 'uuuu' (anio) en vez de 'yyyy' (anio-de-era) para poder validar en modo estricto.
    private static final DateTimeFormatter FMT_VISTA =
            DateTimeFormatter.ofPattern(PATRON_VISTA).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FMT_ISO =
            DateTimeFormatter.ofPattern(PATRON_ISO).withResolverStyle(ResolverStyle.STRICT);

    private DateFormatter() {
    }

    /**
     * Convierte una fecha a texto para mostrar al usuario (dd/MM/yyyy).
     *
     * @param fecha fecha o null.
     * @return texto en formato dd/MM/yyyy, o cadena vacia si la fecha es null.
     */
    public static String aVista(LocalDate fecha) {
        return fecha == null ? "" : fecha.format(FMT_VISTA);
    }

    /**
     * Convierte una fecha a texto ISO para la base de datos (yyyy-MM-dd).
     *
     * @param fecha fecha o null.
     * @return texto ISO, o null si la fecha es null.
     */
    public static String aIso(LocalDate fecha) {
        return fecha == null ? null : fecha.format(FMT_ISO);
    }

    /**
     * Interpreta un texto en formato de vista (dd/MM/yyyy) como fecha.
     *
     * @param texto texto del usuario (puede venir con espacios).
     * @return la fecha, o null si el texto esta vacio.
     * @throws IllegalArgumentException si el texto no es una fecha valida.
     */
    public static LocalDate desdeVista(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim(), FMT_VISTA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Fecha invalida: '" + texto.trim() + "'. Use el formato dd/MM/yyyy (ej. 15/06/2026).");
        }
    }

    /**
     * Interpreta un texto ISO (yyyy-MM-dd) como fecha. Tolerante: si el texto
     * incluye hora (yyyy-MM-dd HH:mm:ss), toma solo la parte de la fecha.
     *
     * @param texto texto ISO o null.
     * @return la fecha, o null si el texto es vacio/invalido.
     */
    public static LocalDate desdeIso(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        String soloFecha = texto.trim();
        int espacio = soloFecha.indexOf(' ');
        if (espacio > 0) {
            soloFecha = soloFecha.substring(0, espacio);
        }
        try {
            return LocalDate.parse(soloFecha, FMT_ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Convierte un texto de vista (dd/MM/yyyy) directamente a ISO (yyyy-MM-dd).
     *
     * @param textoVista texto del usuario.
     * @return texto ISO, o null si el texto esta vacio.
     * @throws IllegalArgumentException si la fecha no es valida.
     */
    public static String vistaAIso(String textoVista) {
        return aIso(desdeVista(textoVista));
    }

    /**
     * Convierte un texto ISO (yyyy-MM-dd, con o sin hora) a texto de vista (dd/MM/yyyy).
     *
     * @param textoIso texto ISO.
     * @return texto en formato dd/MM/yyyy, o cadena vacia si no se puede interpretar.
     */
    public static String isoAVista(String textoIso) {
        return aVista(desdeIso(textoIso));
    }
}
