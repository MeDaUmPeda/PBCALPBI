package com.casademassas.erp_massas.dto;

import java.math.BigDecimal;

/**
 * Efeito da variação de um ingrediente no custo de um prato.
 * custoBase usa o preço do ingrediente no início do período; custoProjetado usa o preço médio das 12 semanas.
 * precoVendaRecomendado aplica a mesma variação de custo ao preço praticado, mantendo a margem percentual.
 */
public record ImpactoPratoDTO(
        Long pratoId,
        String nomePrato,
        BigDecimal quantidadeUtilizada,
        BigDecimal custoBase,
        BigDecimal custoProjetado,
        BigDecimal variacaoCustoPercentual,
        BigDecimal precoVendaPraticado,
        BigDecimal precoVendaRecomendado
) {
}
