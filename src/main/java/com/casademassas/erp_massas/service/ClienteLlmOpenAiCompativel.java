package com.casademassas.erp_massas.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Chama POST {base-url}/chat/completions de qualquer provedor compatível com a API da OpenAI. */
@Component
public class ClienteLlmOpenAiCompativel implements ClienteLlm {

    // Folga para modelos que gastam tokens raciocinando; o tamanho do parágrafo é limitado nas instruções
    private static final int MAXIMO_TOKENS = 1000;

    private final RestClient restClient;
    private final String baseUrl;
    private final String modelo;
    private final String chave;

    public ClienteLlmOpenAiCompativel(@Value("${llm.base-url}") String baseUrl,
                                      @Value("${llm.modelo}") String modelo,
                                      @Value("${llm.api-key:}") String chave) {
        // Conexão + leitura: limite de 8 s para a demonstração não ficar esperando
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(6));
        this.restClient = RestClient.builder().requestFactory(fabrica).build();
        this.baseUrl = baseUrl;
        this.modelo = modelo;
        this.chave = chave;
    }

    @Override
    public boolean configurado() {
        return chave != null && !chave.isBlank();
    }

    @Override
    public String gerar(String instrucoes, String dados) {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "max_tokens", MAXIMO_TOKENS,
                "messages", List.of(
                        Map.of("role", "system", "content", instrucoes),
                        Map.of("role", "user", "content", dados)));

        JsonNode resposta = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + chave)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);

        JsonNode escolha = resposta == null ? null : resposta.path("choices").path(0);
        if (escolha == null || "length".equals(escolha.path("finish_reason").asText())) {
            throw new IllegalStateException("Resposta do LLM ausente ou cortada pelo limite de tokens");
        }
        return escolha.path("message").path("content").asText(null);
    }
}
