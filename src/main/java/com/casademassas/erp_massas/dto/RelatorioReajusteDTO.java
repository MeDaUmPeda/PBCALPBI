package com.casademassas.erp_massas.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class RelatorioReajusteDTO {

    private Long ingredienteId;
    private String nomeIngrediente;
    private BigDecimal precoMedio12Semanas;
    private BigDecimal precoInicialPeriodo;
    private BigDecimal variacaoPercentual;
    private boolean requerReajuste;
    private BigDecimal percentualReajusteAplicavel;
    private String mensagemAnalise;

    // Construtor explícito para garantir a instanciação no AnaliseTrimestralService
    public RelatorioReajusteDTO(Long ingredienteId,
                                String nomeIngrediente,
                                BigDecimal precoMedio12Semanas,
                                BigDecimal precoInicialPeriodo,
                                BigDecimal variacaoPercentual,
                                boolean requerReajuste,
                                BigDecimal percentualReajusteAplicavel,
                                String mensagemAnalise) {
        this.ingredienteId = ingredienteId;
        this.nomeIngrediente = nomeIngrediente;
        this.precoMedio12Semanas = precoMedio12Semanas;
        this.precoInicialPeriodo = precoInicialPeriodo;
        this.variacaoPercentual = variacaoPercentual;
        this.requerReajuste = requerReajuste;
        this.percentualReajusteAplicavel = percentualReajusteAplicavel;
        this.mensagemAnalise = mensagemAnalise;
    }
}