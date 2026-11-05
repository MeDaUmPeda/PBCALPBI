package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.dto.ExplicacaoPrevisaoDTO;
import com.casademassas.erp_massas.dto.ImpactoPratoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO.SemanaPrevistaDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.casademassas.erp_massas.dto.ExplicacaoPrevisaoDTO.Origem.IA;
import static com.casademassas.erp_massas.dto.ExplicacaoPrevisaoDTO.Origem.PADRAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExplicacaoPrevisaoServiceTest {

    private static final String MENSAGEM = "Mensagem padrão da previsão.";
    private static final String PRATO = "Macarrão ao Molho de Tomate";
    private static final String TEXTO_VALIDO =
            "O Tomate, hoje em R$ 12,80, deve ir para R$ 12,38 em 3 semanas (-3,28%). Isso afeta o prato " + PRATO + ".";

    private final ClienteFalso cliente = new ClienteFalso();
    private final ExplicacaoPrevisaoService servico =
            new ExplicacaoPrevisaoService(mock(PrevisaoPrecoService.class), cliente);

    @Test
    void textoValidoChegaComOrigemIA() {
        cliente.resposta = TEXTO_VALIDO;

        ExplicacaoPrevisaoDTO explicacao = servico.explicar(previsao(PRATO, "12.38"));

        assertThat(explicacao.origem()).isEqualTo(IA);
        assertThat(explicacao.texto()).isEqualTo(TEXTO_VALIDO);
    }

    @Test
    void dadosEnviadosTrazemOsNumerosCalculadosESemErrosDoBacktest() {
        cliente.resposta = TEXTO_VALIDO;

        servico.explicar(previsao(PRATO, "12.38"));

        assertThat(cliente.ultimosDados).contains("Tomate", "12.38", PRATO, "\"variacaoPercentual\":-3.28");
        assertThat(cliente.ultimosDados).doesNotContain("mapeModelo");
    }

    @Test
    void semChaveUsaTextoPadraoSemChamarOProvedor() {
        cliente.configurado = false;
        cliente.resposta = TEXTO_VALIDO;

        ExplicacaoPrevisaoDTO explicacao = servico.explicar(previsao(PRATO, "12.38"));

        assertThat(explicacao.origem()).isEqualTo(PADRAO);
        assertThat(explicacao.texto()).isEqualTo(MENSAGEM);
        assertThat(cliente.chamadas).isZero();
    }

    @Test
    void falhaDoProvedorUsaTextoPadrao() {
        cliente.falha = new IllegalStateException("tempo esgotado ou 429");

        ExplicacaoPrevisaoDTO explicacao = servico.explicar(previsao(PRATO, "12.38"));

        assertThat(explicacao.origem()).isEqualTo(PADRAO);
        assertThat(explicacao.texto()).isEqualTo(MENSAGEM);
        assertThat(cliente.chamadas).isEqualTo(1);
    }

    @Test
    void respostaVaziaUsaTextoPadrao() {
        cliente.resposta = "   ";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);

        cliente.resposta = null;
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void numeroInventadoUsaTextoPadrao() {
        cliente.resposta = TEXTO_VALIDO + " O preço pode chegar a R$ 99,00.";

        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void numeroArredondadoEAceito() {
        cliente.resposta = "O Tomate deve ficar perto de R$ 12,4, ou cerca de R$ 12, afetando " + PRATO + ".";

        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(IA);
    }

    @Test
    void numeroComCasasDecimaisQueNaoBatemEDescartado() {
        cliente.resposta = "O Tomate deve ficar em R$ 12,39, afetando " + PRATO + ".";

        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void valorComMilharEReconhecido() {
        cliente.resposta = "O preço atual de R$ 1.234,56 se mantém no prato " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(IA);

        cliente.resposta = "O preço atual de R$ 1.234,57 se mantém no prato " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.39")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void valorPositivoEscritoSemSinalCasaComVariacaoNegativa() {
        cliente.resposta = "Queda de 3,28% no Tomate, afetando " + PRATO + ".";

        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(IA);
    }

    @Test
    void dataEnviadaEAceitaEDataDesconhecidaNao() {
        cliente.resposta = "Em 07/11/2026 ou 07/11 o preço será R$ 12,38, afetando " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(IA);

        cliente.resposta = "Em 25/12 o preço será R$ 12,39, afetando " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.39")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void numeroNoNomeDoPratoNaoEConferido() {
        String prato = "Menu 77 Queijos";
        cliente.resposta = "O preço vai a R$ 12,38, afetando o " + prato + ".";

        assertThat(servico.explicar(previsao(prato, "12.38")).origem()).isEqualTo(IA);
    }

    @Test
    void textoQueNaoCitaOPratoAfetadoUsaTextoPadrao() {
        cliente.resposta = "O Tomate deve ir para R$ 12,38.";

        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void textoComTermoTecnicoUsaTextoPadrao() {
        cliente.resposta = "A semana 4 tem superouReferencia = false no prato " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);

        cliente.resposta = "O horizonteConfiavelSemanas afeta o prato " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);

        cliente.resposta = "A semana 4 está como false no prato " + PRATO + ".";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);
    }

    @Test
    void nomeDePratoComLetraMaiusculaNoMeioNaoEConfundidoComTermoTecnico() {
        String prato = "Massa alDente";
        cliente.resposta = "O preço vai a R$ 12,38, afetando a " + prato + ".";

        assertThat(servico.explicar(previsao(prato, "12.38")).origem()).isEqualTo(IA);
    }

    @Test
    void mesmosDadosUsamOCacheEDadosNovosChamamOProvedor() {
        cliente.resposta = TEXTO_VALIDO;
        servico.explicar(previsao(PRATO, "12.38"));
        cliente.resposta = "Outro texto para " + PRATO + ".";

        assertThat(servico.explicar(previsao(PRATO, "12.38")).texto()).isEqualTo(TEXTO_VALIDO);
        assertThat(cliente.chamadas).isEqualTo(1);

        assertThat(servico.explicar(previsao(PRATO, "12.45")).texto()).isEqualTo("Outro texto para " + PRATO + ".");
        assertThat(cliente.chamadas).isEqualTo(2);
    }

    @Test
    void respostaRejeitadaNaoEntraNoCache() {
        cliente.resposta = "R$ 99,00";
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(PADRAO);

        cliente.resposta = TEXTO_VALIDO;
        assertThat(servico.explicar(previsao(PRATO, "12.38")).origem()).isEqualTo(IA);
        assertThat(cliente.chamadas).isEqualTo(2);
    }

    private static PrevisaoPrecoDTO previsao(String prato, String precoSemana3) {
        LocalDate base = LocalDate.of(2026, 10, 17);
        List<SemanaPrevistaDTO> semanas = List.of(
                semana(1, base.plusWeeks(1), "12.74", "-0.47", true),
                semana(2, base.plusWeeks(2), "12.53", "-2.11", true),
                semana(3, base.plusWeeks(3), precoSemana3, "-3.28", true),
                semana(4, base.plusWeeks(4), "12.37", "-3.36", false));
        ImpactoPratoDTO impacto = new ImpactoPratoDTO(7L, prato, BigDecimal.ONE, new BigDecimal("14.02"),
                new BigDecimal("13.60"), new BigDecimal("-3.00"), new BigDecimal("1234.56"), new BigDecimal("29.10"));
        return new PrevisaoPrecoDTO(3L, "Tomate", new BigDecimal("12.80"), base, semanas, 3,
                new BigDecimal("10"), false, MENSAGEM, List.of(impacto));
    }

    private static SemanaPrevistaDTO semana(int numero, LocalDate data, String preco, String variacao, boolean superou) {
        return new SemanaPrevistaDTO(numero, data, new BigDecimal(preco), new BigDecimal("12.00"),
                new BigDecimal("13.00"), new BigDecimal(variacao), new BigDecimal("1.10"), new BigDecimal("1.35"), superou);
    }

    private static final class ClienteFalso implements ClienteLlm {
        boolean configurado = true;
        String resposta;
        RuntimeException falha;
        String ultimosDados;
        int chamadas;

        @Override
        public boolean configurado() {
            return configurado;
        }

        @Override
        public String gerar(String instrucoes, String dados) {
            chamadas++;
            ultimosDados = dados;
            if (falha != null) {
                throw falha;
            }
            return resposta;
        }
    }
}
