package com.casademassas.erp_massas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Previsão de preço de um ingrediente para as próximas 4 semanas.
 * horizonteConfiavel é a maior semana até a qual o modelo superou a referência em todas as semanas (ou null).
 * Em pratosImpactados, custoBase usa o último preço e custoProjetado usa a previsão no horizonte confiável.
 */
public record PrevisaoPrecoDTO(
        Long ingredienteId,
        String nomeIngrediente,
        BigDecimal ultimoPreco,
        LocalDate dataUltimoRegistro,
        List<SemanaPrevistaDTO> semanas,
        Integer horizonteConfiavel,
        BigDecimal limiteVariacao,
        boolean requerAtencao,
        String mensagem,
        List<ImpactoPratoDTO> pratosImpactados
) {

    /** faixaMin, faixaMax e os MAPEs são null quando o backtest não teve previsões para a semana. */
    public record SemanaPrevistaDTO(
            int semana,
            LocalDate data,
            BigDecimal precoPrevisto,
            BigDecimal faixaMin,
            BigDecimal faixaMax,
            BigDecimal variacaoPercentual,
            BigDecimal mapeModelo,
            BigDecimal mapeReferencia,
            boolean superouReferencia
    ) {
    }
}
