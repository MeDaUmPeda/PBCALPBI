package com.casademassas.erp_massas.dto;

import java.util.List;

/** Uma página de resultados. pagina começa em 0. */
public record PaginaDTO<T>(List<T> itens, long total, int pagina, int tamanho) {
}
