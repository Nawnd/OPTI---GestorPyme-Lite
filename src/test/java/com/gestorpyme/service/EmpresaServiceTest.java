package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.EmpresaConfiguracion;
import com.gestorpyme.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las reglas de validacion de la configuracion de empresa.
 * El caso de nombre vacio falla antes de tocar la base de datos.
 */
class EmpresaServiceTest {

    private final EmpresaService service = new EmpresaService(new EmpresaRepository());

    @Test
    void nombreVacioLanzaValidacion() {
        EmpresaConfiguracion empresa = new EmpresaConfiguracion();
        empresa.setNombreEmpresa("   ");
        assertThrows(ValidacionException.class, () -> service.guardar(empresa));
    }

    @Test
    void correoInvalidoLanzaValidacion() {
        EmpresaConfiguracion empresa = new EmpresaConfiguracion();
        empresa.setNombreEmpresa("Mi Empresa");
        empresa.setCorreo("correo-sin-arroba");
        assertThrows(ValidacionException.class, () -> service.guardar(empresa));
    }
}
