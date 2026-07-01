package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.EmpresaConfiguracion;
import com.gestorpyme.repository.EmpresaRepository;

import java.util.Optional;

/**
 * Servicio de la configuracion de la empresa.
 * Contiene las reglas de negocio (validaciones) antes de persistir.
 * La vista nunca habla con el repositorio directamente: pasa por aqui.
 */
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    public EmpresaService(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    /**
     * Devuelve la configuracion guardada, si existe.
     *
     * @return Optional con la configuracion (vacio si nunca se ha guardado).
     */
    public Optional<EmpresaConfiguracion> obtener() {
        return empresaRepository.obtener();
    }

    /**
     * Valida y guarda la configuracion de la empresa.
     *
     * Reglas:
     * - El nombre de la empresa es obligatorio.
     * - Si se indica correo, debe tener un formato basico (contener '@').
     * - Si la moneda viene vacia, se asume 'COP' por defecto.
     *
     * @param empresa configuracion a guardar.
     * @throws ValidacionException si algun dato no cumple las reglas.
     */
    public void guardar(EmpresaConfiguracion empresa) {
        if (empresa == null) {
            throw new ValidacionException("No hay datos de empresa para guardar.");
        }
        if (esVacio(empresa.getNombreEmpresa())) {
            throw new ValidacionException("El nombre de la empresa es obligatorio.");
        }
        if (!esVacio(empresa.getCorreo()) && !empresa.getCorreo().contains("@")) {
            throw new ValidacionException("El correo no tiene un formato valido.");
        }
        // Normalizacion: moneda por defecto si llega vacia.
        if (esVacio(empresa.getMoneda())) {
            empresa.setMoneda("COP");
        }
        empresaRepository.guardar(empresa);
    }

    /** Indica si una cadena es nula o esta en blanco. */
    private boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
