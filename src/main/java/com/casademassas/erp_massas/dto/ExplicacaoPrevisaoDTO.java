package com.casademassas.erp_massas.dto;

/** Parágrafo que explica a previsão. origem indica se foi escrito pelo LLM ou se é o texto padrão do sistema. */
public record ExplicacaoPrevisaoDTO(String texto, Origem origem) {

    public enum Origem {
        IA,
        PADRAO
    }
}
