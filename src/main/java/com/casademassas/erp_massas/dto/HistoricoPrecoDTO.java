package com.casademassas.erp_massas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricoPrecoDTO(LocalDate dataRegistro, BigDecimal preco) {
}
