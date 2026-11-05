package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.dto.ImpactoPratoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO.SemanaPrevistaDTO;
import com.casademassas.erp_massas.model.HistoricoPreco;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.model.ItemReceita;
import com.casademassas.erp_massas.model.Prato;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrevisaoPrecoServiceTest {

    private final HistoricoPrecoRepository historicoRepository = mock(HistoricoPrecoRepository.class);
    private final IngredienteRepository ingredienteRepository = mock(IngredienteRepository.class);
    private final PratoRepository pratoRepository = mock(PratoRepository.class);

    private final Map<String, Ingrediente> ingredientes = new LinkedHashMap<>();

    @BeforeEach
    void pratosVaziosPorPadrao() {
        when(pratoRepository.findDistinctByItensReceita_Ingrediente_Id(anyLong())).thenReturn(List.of());
    }

    @Test
    void previsaoDoTomateNoSeedTrazPrecoFaixaDataEHorizonte() {
        carregarSeed();

        PrevisaoPrecoDTO previsao = servico("10").prever(id("Tomate"));

        assertThat(previsao.nomeIngrediente()).isEqualTo("Tomate");
        assertThat(previsao.ultimoPreco()).isEqualByComparingTo("12.80");
        assertThat(previsao.dataUltimoRegistro()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(previsao.horizonteConfiavel()).isEqualTo(3);

        SemanaPrevistaDTO semana1 = previsao.semanas().get(0);
        assertThat(semana1.semana()).isEqualTo(1);
        assertThat(semana1.data()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(semana1.precoPrevisto()).isEqualByComparingTo("12.73");
        assertThat(semana1.faixaMin()).isEqualByComparingTo("12.54");
        assertThat(semana1.faixaMax()).isEqualByComparingTo("12.93");
        assertThat(semana1.mapeModelo()).isEqualByComparingTo("1.06");
        assertThat(semana1.mapeReferencia()).isEqualByComparingTo("1.35");
        assertThat(semana1.superouReferencia()).isTrue();

        SemanaPrevistaDTO semana3 = previsao.semanas().get(2);
        assertThat(semana3.data()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(semana3.precoPrevisto()).isEqualByComparingTo("12.65");
        // (12,65 - 12,80) / 12,80 = -1,17%
        assertThat(semana3.variacaoPercentual()).isEqualByComparingTo("-1.17");

        assertThat(previsao.semanas().get(3).superouReferencia()).isFalse();
    }

    @Test
    void alertaComparaAVariacaoNoHorizonteConfiavelComOLimite() {
        carregarSeed();

        // Variação do Tomate na semana 3: -1,17%
        assertThat(servico("-5").prever(id("Tomate")).requerAtencao()).isTrue();
        assertThat(servico("-1").prever(id("Tomate")).requerAtencao()).isFalse();
    }

    @Test
    void impactoUsaOUltimoPrecoComoBaseEAPrevisaoNoHorizonteComoProjetado() {
        carregarSeed();
        Ingrediente tomate = ingredientes.get("Tomate");
        Ingrediente farinha = ingredientes.get("Farinha");
        farinha.setPrecoCustoAtual(new BigDecimal("6.10"));
        Prato prato = new Prato(7L, "Macarrão", new ArrayList<>(), null, null, new BigDecimal("30.00"));
        prato.getItensReceita().add(new ItemReceita(1L, prato, tomate, BigDecimal.ONE));
        prato.getItensReceita().add(new ItemReceita(2L, prato, farinha, new BigDecimal("0.2")));
        when(pratoRepository.findDistinctByItensReceita_Ingrediente_Id(tomate.getId())).thenReturn(List.of(prato));

        PrevisaoPrecoDTO previsao = servico("10").prever(tomate.getId());

        ImpactoPratoDTO impacto = previsao.pratosImpactados().get(0);
        // Base: 12,80 + 6,10 × 0,2 = 14,02. Projetado: 12,65 + 1,22 = 13,87.
        assertThat(impacto.custoBase()).isEqualByComparingTo("14.02");
        assertThat(impacto.custoProjetado()).isEqualByComparingTo("13.87");
        assertThat(impacto.variacaoCustoPercentual()).isEqualByComparingTo("-1.07");
        assertThat(impacto.precoVendaRecomendado()).isEqualByComparingTo("29.68");
        assertThat(previsao.mensagem()).contains("Macarrão");
    }

    @Test
    void semHorizonteConfiavelNaoHaAlertaNemImpacto() {
        // Preços constantes: modelo e referência erram 0%, então o modelo não supera a referência
        Ingrediente a = adicionarConstante(1L, "A");
        adicionarConstante(2L, "B");
        carregarHistorico();
        Prato prato = new Prato(7L, "Prato", new ArrayList<>(), null, null, new BigDecimal("30.00"));
        prato.getItensReceita().add(new ItemReceita(1L, prato, a, BigDecimal.ONE));
        when(pratoRepository.findDistinctByItensReceita_Ingrediente_Id(a.getId())).thenReturn(List.of(prato));

        PrevisaoPrecoDTO previsao = servico("-100").prever(a.getId());

        assertThat(previsao.horizonteConfiavel()).isNull();
        assertThat(previsao.requerAtencao()).isFalse();
        assertThat(previsao.pratosImpactados()).isEmpty();
        assertThat(previsao.semanas()).hasSize(4);
    }

    @Test
    void ingredienteInexistenteResultaEm404() {
        when(ingredienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico("10").prever(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void menosDeDozeRegistrosResultaEm422EOIngredienteFicaForaDoTreino() {
        carregarSeed();
        Ingrediente novo = new Ingrediente(100L, "Novo", "KG", BigDecimal.TEN);
        when(ingredienteRepository.findById(100L)).thenReturn(Optional.of(novo));
        List<HistoricoPreco> historico = new ArrayList<>(historicoRepository.findAllByOrderByIngredienteIdAscDataRegistroAsc());
        for (int i = 0; i < 11; i++) {
            historico.add(new HistoricoPreco(null, novo, BigDecimal.TEN, LocalDate.of(2026, 8, 1).plusWeeks(i)));
        }
        when(historicoRepository.findAllByOrderByIngredienteIdAscDataRegistroAsc()).thenReturn(historico);

        assertThatThrownBy(() -> servico("10").prever(100L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        // O ingrediente incompleto não muda o resultado dos demais
        assertThat(servico("10").prever(id("Tomate")).semanas().get(2).precoPrevisto()).isEqualByComparingTo("12.65");
    }

    @Test
    void precoZeradoResultaEm422() {
        Ingrediente a = adicionarConstante(1L, "A");
        adicionarConstante(2L, "B");
        adicionarConstante(3L, "C");
        List<HistoricoPreco> historico = carregarHistorico();
        historico.get(11).setPreco(BigDecimal.ZERO);

        assertThatThrownBy(() -> servico("10").prever(a.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void poucosExemplosParaOsModelosFinaisResultaEm422() {
        // Só um ingrediente: 5 exemplos na semana 4, abaixo do mínimo de 8
        Ingrediente a = adicionarConstante(1L, "A");
        carregarHistorico();

        assertThatThrownBy(() -> servico("10").prever(a.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private final List<HistoricoPreco> registros = new ArrayList<>();

    private PrevisaoPrecoService servico(String limite) {
        AnaliseTrimestralService analise = new AnaliseTrimestralService(
                historicoRepository, ingredienteRepository, pratoRepository, new BigDecimal(limite));
        return new PrevisaoPrecoService(historicoRepository, ingredienteRepository, pratoRepository,
                analise, new BigDecimal(limite));
    }

    private Long id(String nome) {
        return ingredientes.get(nome).getId();
    }

    private void carregarSeed() {
        long proximoId = 1;
        for (Map.Entry<String, TreeMap<String, Double>> entrada : DadosSeed.historicos().entrySet()) {
            Ingrediente ingrediente = registrar(proximoId++, entrada.getKey());
            entrada.getValue().forEach((data, preco) -> registros.add(new HistoricoPreco(
                    null, ingrediente, BigDecimal.valueOf(preco), LocalDate.parse(data))));
        }
        carregarHistorico();
    }

    private Ingrediente adicionarConstante(Long id, String nome) {
        Ingrediente ingrediente = registrar(id, nome);
        for (int i = 0; i < 12; i++) {
            registros.add(new HistoricoPreco(null, ingrediente, new BigDecimal("5.00"),
                    LocalDate.of(2026, 8, 1).plusWeeks(i)));
        }
        return ingrediente;
    }

    private Ingrediente registrar(Long id, String nome) {
        Ingrediente ingrediente = new Ingrediente(id, nome, "KG", BigDecimal.ONE);
        ingredientes.put(nome, ingrediente);
        when(ingredienteRepository.findById(id)).thenReturn(Optional.of(ingrediente));
        return ingrediente;
    }

    private List<HistoricoPreco> carregarHistorico() {
        when(historicoRepository.findAllByOrderByIngredienteIdAscDataRegistroAsc()).thenReturn(registros);
        return registros;
    }
}
