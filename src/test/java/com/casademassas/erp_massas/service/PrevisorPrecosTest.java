package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.service.PrevisorPrecos.AvaliacaoSemana;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PrevisorPrecosTest {

    private static final int ULTIMO_CORTE = 11;

    @Test
    void backtestDoSeedReproduzOsErrosDoPrototipoIndependente() {
        List<AvaliacaoSemana> avaliacoes = PrevisorPrecos.avaliar(DadosSeed.series());

        assertThat(avaliacoes).hasSize(4);
        confere(avaliacoes.get(0), 1, 84, 1.0645, 1.3475, 0.015459);
        confere(avaliacoes.get(1), 2, 70, 1.3143, 2.0390, 0.018674);
        confere(avaliacoes.get(2), 3, 42, 1.3750, 2.5196, 0.021334);
        confere(avaliacoes.get(3), 4, 14, 1.9780, 1.9693, 0.041969);
    }

    @Test
    void semanaQuatroDoSeedNaoSuperaAReferenciaEAsDemaisSuperam() {
        List<AvaliacaoSemana> avaliacoes = PrevisorPrecos.avaliar(DadosSeed.series());

        assertThat(avaliacoes).extracting(AvaliacaoSemana::superouReferencia)
                .containsExactly(true, true, true, false);
    }

    @Test
    void previsaoFinalDoSeedReproduzOPrototipo() {
        List<double[]> series = DadosSeed.series();
        double[] tomate = DadosSeed.serie("Tomate");
        double[] farinha = DadosSeed.serie("Farinha");
        double[] esperadoTomate = {12.7335, 12.6820, 12.6478, 12.6218};
        double[] esperadoFarinha = {6.0833, 6.0704, 6.0617, 6.0552};

        for (int semana = 1; semana <= 4; semana++) {
            RegressaoRidge modelo = PrevisorPrecos.treinar(series, ULTIMO_CORTE, semana).orElseThrow();
            assertThat(PrevisorPrecos.prever(modelo, tomate, ULTIMO_CORTE))
                    .isCloseTo(esperadoTomate[semana - 1], within(1e-3));
            assertThat(PrevisorPrecos.prever(modelo, farinha, ULTIMO_CORTE))
                    .isCloseTo(esperadoFarinha[semana - 1], within(1e-3));
        }
    }

    @Test
    void treinoNaoEnxergaPrecosPosterioresAoCorte() {
        List<double[]> originais = DadosSeed.series();
        List<double[]> comFuturoAlterado = new ArrayList<>();
        for (double[] serie : originais) {
            double[] copia = serie.clone();
            for (int t = 8; t < copia.length; t++) {
                copia[t] = copia[t] * 50;
            }
            comFuturoAlterado.add(copia);
        }
        int corte = 7;
        int semana = 2;

        double[] tomate = DadosSeed.serie("Tomate");
        double previsaoOriginal = PrevisorPrecos.prever(
                PrevisorPrecos.treinar(originais, corte, semana).orElseThrow(), tomate, corte);
        double previsaoAlterada = PrevisorPrecos.prever(
                PrevisorPrecos.treinar(comFuturoAlterado, corte, semana).orElseThrow(), tomate, corte);

        assertThat(previsaoAlterada).isEqualTo(previsaoOriginal);
    }

    @Test
    void precoDoProprioCorteEntraNoTreino() {
        List<double[]> originais = DadosSeed.series();
        List<double[]> comCorteAlterado = new ArrayList<>();
        for (double[] serie : originais) {
            double[] copia = serie.clone();
            copia[7] = copia[7] * 50;
            comCorteAlterado.add(copia);
        }
        double[] tomate = DadosSeed.serie("Tomate");

        double original = PrevisorPrecos.prever(PrevisorPrecos.treinar(originais, 7, 2).orElseThrow(), tomate, 7);
        double alterada = PrevisorPrecos.prever(PrevisorPrecos.treinar(comCorteAlterado, 7, 2).orElseThrow(), tomate, 7);

        assertThat(alterada).isNotEqualTo(original);
    }

    @Test
    void naoTreinaComMenosDeOitoExemplos() {
        double[] umaSerie = DadosSeed.serie("Tomate");

        // Uma série na semana 4: t de 3 a 7, ou seja, 5 exemplos
        assertThat(PrevisorPrecos.treinar(List.of(umaSerie), 11, 4)).isEqualTo(Optional.empty());
        // Uma série na semana 1: t de 3 a 10, ou seja, 8 exemplos
        assertThat(PrevisorPrecos.treinar(List.of(umaSerie), 11, 1)).isPresent();
    }

    @Test
    void semPrevisoesNoBacktestASemanaNaoSuperaAReferencia() {
        double[] umaSerie = DadosSeed.serie("Tomate");

        AvaliacaoSemana semana4 = PrevisorPrecos.avaliar(List.of(umaSerie)).get(3);

        assertThat(semana4.previsoes()).isZero();
        assertThat(semana4.superouReferencia()).isFalse();
    }

    @Test
    void horizonteConfiavelParaNaPrimeiraSemanaSemGanho() {
        assertThat(PrevisorPrecos.horizonteConfiavel(avaliacoes(true, true, false, true))).isEqualTo(2);
        assertThat(PrevisorPrecos.horizonteConfiavel(avaliacoes(true, true, true, true))).isEqualTo(4);
        assertThat(PrevisorPrecos.horizonteConfiavel(avaliacoes(false, true, true, true))).isNull();
    }

    private static List<AvaliacaoSemana> avaliacoes(boolean... superou) {
        List<AvaliacaoSemana> lista = new ArrayList<>();
        for (int i = 0; i < superou.length; i++) {
            lista.add(new AvaliacaoSemana(i + 1, 10, superou[i] ? 1 : 2, superou[i] ? 2 : 1, 0.01));
        }
        return lista;
    }

    private static void confere(AvaliacaoSemana avaliacao, int semana, int previsoes,
                                double mapeModelo, double mapeReferencia, double erroP80) {
        assertThat(avaliacao.semana()).isEqualTo(semana);
        assertThat(avaliacao.previsoes()).isEqualTo(previsoes);
        assertThat(avaliacao.mapeModelo()).isCloseTo(mapeModelo, within(0.001));
        assertThat(avaliacao.mapeReferencia()).isCloseTo(mapeReferencia, within(0.001));
        assertThat(avaliacao.erroP80()).isCloseTo(erroP80, within(0.00001));
    }
}
