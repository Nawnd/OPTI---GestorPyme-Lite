package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.repository.CuentaRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de cuentas por cobrar. El saldo se calcula aqui/repositorio
 * (valor_total - valor_pagado), nunca desde la vista. Capa: service.
 */
public class CuentaService {

    private final CuentaRepository cuentaRepository;

    public CuentaService() {
        this(new CuentaRepository());
    }

    public CuentaService(CuentaRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    /** Lista las cuentas por cobrar. */
    public List<CuentaPorCobrar> listar() throws SQLException {
        return cuentaRepository.listar();
    }

    /**
     * Busca la cuenta por cobrar de una venta (si existe). Usado por el modulo Pagos
     * para decidir si el cobro debe tratarse como abono de cartera.
     *
     * @param idVenta identificador de la venta.
     * @return un {@link Optional} con la cuenta, o vacio si la venta no tiene cuenta.
     * @throws SQLException ante errores de base de datos.
     */
    public Optional<CuentaPorCobrar> buscarPorVenta(int idVenta) throws SQLException {
        return cuentaRepository.buscarPorVenta(idVenta);
    }

    /** Lista los abonos de una cuenta. */
    public List<Abono> listarAbonos(int idCuenta) throws SQLException {
        return cuentaRepository.listarAbonos(idCuenta);
    }

    /**
     * Valida y registra un abono. El tope contra el saldo se verifica de forma
     * atomica en el repositorio (dentro de la transaccion).
     *
     * @param abono abono a registrar (id_cuenta, valor, medio).
     * @throws ValidacionException si el valor es &lt;= 0 (o, en el repositorio, si supera el saldo).
     * @throws SQLException        ante errores de base de datos.
     */
    public void registrarAbono(Abono abono) throws SQLException {
        if (abono == null) {
            throw new ValidacionException("El abono no puede ser nulo.");
        }
        if (abono.getValor() == null || abono.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("El valor del abono debe ser mayor a 0.");
        }
        cuentaRepository.registrarAbono(abono);
    }
}
