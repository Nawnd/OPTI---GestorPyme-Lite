package com.gestorpyme.util;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Período de análisis del Dashboard Ejecutivo (Paso K). Capa: util (cálculo puro, sin BD ni Swing).
 *
 * Traduce año + mes + semana operativa a un rango de fechas ISO {@code fechaInicio..fechaFin} y una
 * etiqueta legible. Semanas operativas (no ISO): S1=1-7, S2=8-14, S3=15-21, S4=22-fin de mes.
 * Reglas: mes null = año completo; semana null = mes completo.
 */
public class PeriodoDashboard {

    private final int anio;
    private final Integer mes;     // null = año completo
    private final Integer semana;  // null = mes completo (1..4 = semana operativa)
    private final String fechaInicio; // 'YYYY-MM-DD'
    private final String fechaFin;    // 'YYYY-MM-DD'
    private final String etiqueta;

    private PeriodoDashboard(int anio, Integer mes, Integer semana,
                             LocalDate inicio, LocalDate fin, String etiqueta) {
        this.anio = anio;
        this.mes = mes;
        this.semana = semana;
        this.fechaInicio = inicio.toString();
        this.fechaFin = fin.toString();
        this.etiqueta = etiqueta;
    }

    /**
     * Construye el período. Valida rangos y deriva fechas y etiqueta.
     *
     * @param anio   año (p. ej. 2026).
     * @param mes    1..12, o null para el año completo.
     * @param semana 1..4 (semana operativa), o null para el mes completo. Se ignora si mes es null.
     * @throws IllegalArgumentException si mes o semana están fuera de rango.
     */
    public static PeriodoDashboard de(int anio, Integer mes, Integer semana) {
        if (mes != null && (mes < 1 || mes > 12)) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12.");
        }
        if (semana != null && (semana < 1 || semana > 4)) {
            throw new IllegalArgumentException("La semana operativa debe estar entre 1 y 4.");
        }
        // Año completo.
        if (mes == null) {
            LocalDate ini = LocalDate.of(anio, 1, 1);
            LocalDate fin = LocalDate.of(anio, 12, 31);
            return new PeriodoDashboard(anio, null, null, ini, fin, "Año " + anio);
        }
        YearMonth ym = YearMonth.of(anio, mes);
        int ultimoDia = ym.lengthOfMonth();
        // Mes completo.
        if (semana == null) {
            LocalDate ini = LocalDate.of(anio, mes, 1);
            LocalDate fin = LocalDate.of(anio, mes, ultimoDia);
            return new PeriodoDashboard(anio, mes, null, ini, fin, String.format("%02d/%d", mes, anio));
        }
        // Semana operativa: S1 1-7, S2 8-14, S3 15-21, S4 22-fin.
        int diaIni = (semana - 1) * 7 + 1;
        int diaFin = semana == 4 ? ultimoDia : semana * 7;
        LocalDate ini = LocalDate.of(anio, mes, diaIni);
        LocalDate fin = LocalDate.of(anio, mes, diaFin);
        return new PeriodoDashboard(anio, mes, semana, ini, fin,
                String.format("Sem %d %02d/%d", semana, mes, anio));
    }

    public int getAnio() { return anio; }
    public Integer getMes() { return mes; }
    public Integer getSemana() { return semana; }
    public String getFechaInicio() { return fechaInicio; }
    public String getFechaFin() { return fechaFin; }
    public String getEtiqueta() { return etiqueta; }
}
