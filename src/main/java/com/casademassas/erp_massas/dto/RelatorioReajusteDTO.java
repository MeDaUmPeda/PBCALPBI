package com.casademassas.erp_massas.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RelatorioReajusteDTO {

    private Long ingredienteId;
    private String nomeIngrediente;
    private BigDecimal precoMedio12Semanas;
    private BigDecimal precoInicialPeriodo;
    private BigDecimal variacaoPercentual;
    private BigDecimal limiteVariacao;
    private boolean requerReajuste;
    private String mensagemAnalise;
    private List<ImpactoPratoDTO> pratosImpactados;

    // Construtor explícito para garantir a instanciação no AnaliseTrimestralService
    public RelatorioReajusteDTO(Long ingredienteId,
                                String nomeIngrediente,
                                BigDecimal precoMedio12Semanas,
                                BigDecimal precoInicialPeriodo,
                                BigDecimal variacaoPercentual,
                                BigDecimal limiteVariacao,
                                boolean requerReajuste,
                                String mensagemAnalise,
                                List<ImpactoPratoDTO> pratosImpactados) {
        this.ingredienteId = ingredienteId;
        this.nomeIngrediente = nomeIngrediente;
        this.precoMedio12Semanas = precoMedio12Semanas;
        this.precoInicialPeriodo = precoInicialPeriodo;
        this.variacaoPercentual = variacaoPercentual;
        this.limiteVariacao = limiteVariacao;
        this.requerReajuste = requerReajuste;
        this.mensagemAnalise = mensagemAnalise;
        this.pratosImpactados = pratosImpactados;
    }
}
