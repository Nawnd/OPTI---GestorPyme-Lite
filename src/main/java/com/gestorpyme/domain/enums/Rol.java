package com.gestorpyme.domain.enums;

/**
 * Roles de usuario del sistema. El valor se almacena como TEXT en la columna
 * usuarios.rol; el nombre de cada constante coincide EXACTAMENTE con el valor
 * guardado en la base de datos.
 *
 * Permisos previstos (DEC-021, implementacion progresiva desde ADMIN):
 *  - ADMIN: acceso total.
 *  - VENDEDOR: ventas, clientes y pagos.
 *  - INVENTARIO: productos, bodegas, inventario, Kardex, lotes y vencimientos.
 *  - CONSULTA: solo lectura (dashboard y reportes).
 */
public enum Rol {

    ADMIN("Acceso total al sistema"),
    VENDEDOR("Ventas, clientes y pagos"),
    INVENTARIO("Productos, bodegas, inventario, Kardex, lotes y vencimientos"),
    CONSULTA("Solo lectura: dashboard y reportes");

    /** Texto descriptivo del alcance del rol (uso informativo en la interfaz). */
    private final String descripcion;

    Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del rol (p. ej. "ADMIN"); se ignoran espacios alrededor.
     * @return el {@link Rol} correspondiente.
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es un rol valido.
     */
    public static Rol desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El rol no puede ser nulo o vacio");
        }
        try {
            return Rol.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rol no valido: '" + valor + "'", e);
        }
    }
}
