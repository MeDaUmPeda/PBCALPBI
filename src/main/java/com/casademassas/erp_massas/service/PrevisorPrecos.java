package com.casademassas.erp_massas.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Aprende com séries de 12 preços semanais e prevê as próximas 4 semanas.
 * Há um modelo por semana prevista, treinado com todos os ingredientes juntos.
 * A entrada é o desvio do preço atual em relação à média das últimas 4 semanas, e a saída é a variação
 * até a semana prevista, ambas em logaritmo, o que permite misturar unidades (kg, dúzia...).
 * Sem intercepto: um preço igual à própria média prevê preço igual, sem carregar a tendência do período.
 */
final class PrevisorPrecos {

    static final int REGISTROS_POR_SERIE = 12;
    static final int SEMANAS_PREVISTAS = 4;
    static final int ULTIMO_INDICE = REGISTROS_POR_SERIE - 1;

    private static final int PRIMEIRO_T = 3;
    private static final int PRIMEIRO_CORTE = 5;
    private static final int MINIMO_EXEMPLOS = 8;
    private static final double LAMBDA = 0.01;
    private static final double PERCENTIL_FAIXA = 0.8;

    private PrevisorPrecos() {
    }

    /** Resultado do backtest de uma semana. MAPEs em pontos percentuais; erroP80 como fração. */
    record AvaliacaoSemana(int semana, int previsoes, double mapeModelo, double mapeReferencia, double erroP80) {
        boolean superouReferencia() {
            return previsoes > 0 && mapeModelo < mapeReferencia;
        }
    }

    /**
     * Treina com os preços até o índice {@code corte}: a saída de cada exemplo é p[t + semana],
     * portanto t vai de 3 até corte - semana. Vazio se houver menos de 8 exemplos.
     */
    static Optional<RegressaoRidge> treinar(List<double[]> series, int corte, int semana) {
        List<double[]> entradas = new ArrayList<>();
        List<Double> saidas = new ArrayList<>();
        for (double[] serie : series) {
            for (int t = PRIMEIRO_T; t <= corte - semana; t++) {
                entradas.add(entrada(serie, t));
                saidas.add(Math.log(serie[t + semana] / serie[t]));
            }
        }
        if (entradas.size() < MINIMO_EXEMPLOS) {
            return Optional.empty();
        }
        return Optional.of(RegressaoRidge.treinarSemIntercepto(
                entradas.toArray(new double[0][]),
                saidas.stream().mapToDouble(Double::doubleValue).toArray(),
                LAMBDA));
    }

    static double prever(RegressaoRidge modelo, double[] serie, int corte) {
        return serie[corte] * Math.exp(modelo.prever(entrada(serie, corte)));
    }

    /** Backtest: em cada ponto de corte, treina só com o passado e compara com a regra "preço igual ao último". */
    static List<AvaliacaoSemana> avaliar(List<double[]> series) {
        List<AvaliacaoSemana> avaliacoes = new ArrayList<>();
        for (int semana = 1; semana <= SEMANAS_PREVISTAS; semana++) {
            List<Double> errosModelo = new ArrayList<>();
            double somaErrosReferencia = 0;
            for (int corte = PRIMEIRO_CORTE; corte + semana <= ULTIMO_INDICE; corte++) {
                Optional<RegressaoRidge> modelo = treinar(series, corte, semana);
                if (modelo.isEmpty()) {
                    continue;
                }
                for (double[] serie : series) {
                    double real = serie[corte + semana];
                    errosModelo.add(Math.abs(prever(modelo.get(), serie, corte) - real) / real);
                    somaErrosReferencia += Math.abs(serie[corte] - real) / real;
                }
            }
            avaliacoes.add(resumir(semana, errosModelo, somaErrosReferencia));
        }
        return avaliacoes;
    }

    /** Maior semana tal que todas, da 1 até ela, superaram a referência. Null se a primeira não superou. */
    static Integer horizonteConfiavel(List<AvaliacaoSemana> avaliacoes) {
        Integer horizonte = null;
        for (AvaliacaoSemana avaliacao : avaliacoes) {
            if (!avaliacao.superouReferencia()) {
                break;
            }
            horizonte = avaliacao.semana();
        }
        return horizonte;
    }

    private static double[] entrada(double[] serie, int t) {
        double media = (serie[t] + serie[t - 1] + serie[t - 2] + serie[t - 3]) / 4;
        return new double[]{Math.log(serie[t] / media)};
    }

    private static AvaliacaoSemana resumir(int semana, List<Double> errosModelo, double somaErrosReferencia) {
        int previsoes = errosModelo.size();
        if (previsoes == 0) {
            return new AvaliacaoSemana(semana, 0, Double.NaN, Double.NaN, Double.NaN);
        }
        double[] ordenados = errosModelo.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double mapeModelo = 100 * Arrays.stream(ordenados).sum() / previsoes;
        double mapeReferencia = 100 * somaErrosReferencia / previsoes;
        double erroP80 = ordenados[(int) Math.ceil(PERCENTIL_FAIXA * previsoes) - 1];
        return new AvaliacaoSemana(semana, previsoes, mapeModelo, mapeReferencia, erroP80);
    }
}
