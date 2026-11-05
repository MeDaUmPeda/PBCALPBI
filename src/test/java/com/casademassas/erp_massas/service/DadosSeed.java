package com.casademassas.erp_massas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Lê os preços de dados_iniciais.json, por nome do ingrediente e em ordem de data. */
final class DadosSeed {

    private DadosSeed() {
    }

    /** Para cada ingrediente, as datas ISO (chave) e os preços, em ordem crescente. */
    static Map<String, TreeMap<String, Double>> historicos() {
        try (InputStream entrada = DadosSeed.class.getResourceAsStream("/dados_iniciais.json")) {
            JsonNode raiz = new ObjectMapper().readTree(entrada);
            Map<String, TreeMap<String, Double>> historicos = new LinkedHashMap<>();
            for (JsonNode registro : raiz.get("historicoPrecos")) {
                historicos.computeIfAbsent(registro.get("nomeIngrediente").asText(), nome -> new TreeMap<>())
                        .put(registro.get("dataRegistro").asText(), registro.get("preco").asDouble());
            }
            return historicos;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static List<double[]> series() {
        List<double[]> series = new ArrayList<>();
        historicos().values().forEach(h -> series.add(h.values().stream().mapToDouble(Double::doubleValue).toArray()));
        return series;
    }

    static double[] serie(String nome) {
        return historicos().get(nome).values().stream().mapToDouble(Double::doubleValue).toArray();
    }
}
