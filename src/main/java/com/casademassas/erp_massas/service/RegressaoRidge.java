package com.casademassas.erp_massas.service;

/**
 * Regressão linear com penalização L2 (Ridge). Resolve (XᵀX + λI) w = Xᵀy.
 * Com intercepto, entradas e saídas são centralizadas e o intercepto não é penalizado.
 */
final class RegressaoRidge {

    private final double[] coeficientes;
    private final double intercepto;

    private RegressaoRidge(double[] coeficientes, double intercepto) {
        this.coeficientes = coeficientes;
        this.intercepto = intercepto;
    }

    static RegressaoRidge treinar(double[][] entradas, double[] saidas, double lambda) {
        int variaveis = entradas[0].length;
        double[] mediasEntrada = new double[variaveis];
        double mediaSaida = 0;
        for (int i = 0; i < entradas.length; i++) {
            mediaSaida += saidas[i] / entradas.length;
            for (int j = 0; j < variaveis; j++) {
                mediasEntrada[j] += entradas[i][j] / entradas.length;
            }
        }
        return ajustar(entradas, saidas, lambda, mediasEntrada, mediaSaida);
    }

    /** Sem intercepto: entrada zero sempre prevê saída zero. */
    static RegressaoRidge treinarSemIntercepto(double[][] entradas, double[] saidas, double lambda) {
        return ajustar(entradas, saidas, lambda, new double[entradas[0].length], 0);
    }

    private static RegressaoRidge ajustar(double[][] entradas, double[] saidas, double lambda,
                                          double[] mediasEntrada, double mediaSaida) {
        int variaveis = entradas[0].length;

        double[][] matriz = new double[variaveis][variaveis + 1];
        for (int i = 0; i < entradas.length; i++) {
            for (int a = 0; a < variaveis; a++) {
                double centradoA = entradas[i][a] - mediasEntrada[a];
                for (int b = 0; b < variaveis; b++) {
                    matriz[a][b] += centradoA * (entradas[i][b] - mediasEntrada[b]);
                }
                matriz[a][variaveis] += centradoA * (saidas[i] - mediaSaida);
            }
        }
        for (int a = 0; a < variaveis; a++) {
            matriz[a][a] += lambda;
        }

        double[] coeficientes = resolver(matriz);
        double intercepto = mediaSaida;
        for (int j = 0; j < variaveis; j++) {
            intercepto -= coeficientes[j] * mediasEntrada[j];
        }
        return new RegressaoRidge(coeficientes, intercepto);
    }

    double prever(double[] entrada) {
        double resultado = intercepto;
        for (int j = 0; j < coeficientes.length; j++) {
            resultado += coeficientes[j] * entrada[j];
        }
        return resultado;
    }

    /** Eliminação de Gauss com pivoteamento parcial sobre a matriz aumentada. */
    private static double[] resolver(double[][] matriz) {
        int n = matriz.length;
        for (int coluna = 0; coluna < n; coluna++) {
            int pivo = coluna;
            for (int linha = coluna + 1; linha < n; linha++) {
                if (Math.abs(matriz[linha][coluna]) > Math.abs(matriz[pivo][coluna])) {
                    pivo = linha;
                }
            }
            double[] troca = matriz[coluna];
            matriz[coluna] = matriz[pivo];
            matriz[pivo] = troca;

            for (int linha = 0; linha < n; linha++) {
                if (linha != coluna) {
                    double fator = matriz[linha][coluna] / matriz[coluna][coluna];
                    for (int k = coluna; k <= n; k++) {
                        matriz[linha][k] -= fator * matriz[coluna][k];
                    }
                }
            }
        }
        double[] solucao = new double[n];
        for (int i = 0; i < n; i++) {
            solucao[i] = matriz[i][n] / matriz[i][i];
        }
        return solucao;
    }
}
