package com.casademassas.erp_massas.service;

/** Gera texto com um LLM. Qualquer falha é lançada como exceção; quem chama decide o que fazer. */
public interface ClienteLlm {

    boolean configurado();

    String gerar(String instrucoes, String dados);
}
