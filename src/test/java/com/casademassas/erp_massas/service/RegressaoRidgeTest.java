package com.casademassas.erp_massas.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegressaoRidgeTest {

    @Test
    void recuperaCoeficientesDeUmaRelacaoLinearExata() {
        // y = 3 + 2*x1 - x2
        double[][] entradas = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 3}};
        double[] saidas = {3, 5, 2, 4, 6, 2};

        RegressaoRidge modelo = RegressaoRidge.treinar(entradas, saidas, 1e-9);

        assertThat(modelo.prever(new double[]{0, 0})).isCloseTo(3, within(1e-6));
        assertThat(modelo.prever(new double[]{1, 0})).isCloseTo(5, within(1e-6));
        assertThat(modelo.prever(new double[]{0, 1})).isCloseTo(2, within(1e-6));
    }

    @Test
    void lambdaEncolheOCoeficienteSemPenalizarOIntercepto() {
        // Sxy = 10, Sxx = 5, lambda = 10: inclinação = 10/15; intercepto = 5 - (10/15) * 2,5
        double[][] entradas = {{1}, {2}, {3}, {4}};
        double[] saidas = {2, 4, 6, 8};

        RegressaoRidge modelo = RegressaoRidge.treinar(entradas, saidas, 10);

        assertThat(modelo.prever(new double[]{0})).isCloseTo(3.3333, within(1e-3));
        assertThat(modelo.prever(new double[]{3})).isCloseTo(5.3333, within(1e-3));
    }

    @Test
    void semInterceptoAPrevisaoPassaPelaOrigem() {
        // y = 2x + 10. Sem intercepto: Σxy = 160, Σx² = 30, lambda = 10: inclinação = 160/40 = 4
        double[][] entradas = {{1}, {2}, {3}, {4}};
        double[] saidas = {12, 14, 16, 18};

        RegressaoRidge modelo = RegressaoRidge.treinarSemIntercepto(entradas, saidas, 10);

        assertThat(modelo.prever(new double[]{0})).isCloseTo(0, within(1e-9));
        assertThat(modelo.prever(new double[]{2})).isCloseTo(8, within(1e-9));
    }
}
