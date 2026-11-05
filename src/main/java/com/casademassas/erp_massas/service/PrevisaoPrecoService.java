package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.dto.ImpactoPratoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO.SemanaPrevistaDTO;
import com.casademassas.erp_massas.model.HistoricoPreco;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import com.casademassas.erp_massas.service.PrevisorPrecos.AvaliacaoSemana;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PrevisaoPrecoService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final HistoricoPrecoRepository historicoRepository;
    private final IngredienteRepository ingredienteRepository;
    private final PratoRepository pratoRepository;
    private final AnaliseTrimestralService analiseTrimestralService;
    private final BigDecimal limiteVariacao;

    public PrevisaoPrecoService(HistoricoPrecoRepository historicoRepository,
                                IngredienteRepository ingredienteRepository,
                                PratoRepository pratoRepository,
                                AnaliseTrimestralService analiseTrimestralService,
                                @Value("${analise.limite-variacao:10}") BigDecimal limiteVariacao) {
        this.historicoRepository = historicoRepository;
        this.ingredienteRepository = ingredienteRepository;
        this.pratoRepository = pratoRepository;
        this.analiseTrimestralService = analiseTrimestralService;
        this.limiteVariacao = limiteVariacao;
    }

    @Transactional(readOnly = true)
    public PrevisaoPrecoDTO prever(Long ingredienteId) {
        Ingrediente ingrediente = ingredienteRepository.findById(ingredienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ingrediente não encontrado ID: " + ingredienteId));

        Map<Long, List<HistoricoPreco>> ultimosRegistros = ultimosRegistrosPorIngrediente();
        List<HistoricoPreco> registros = ultimosRegistros.getOrDefault(ingredienteId, List.of());
        if (!utilizavel(registros)) {
            throw dadosInsuficientes("São necessários 12 registros de preço válidos para prever este ingrediente.");
        }

        List<double[]> series = ultimosRegistros.values().stream()
                .filter(PrevisaoPrecoService::utilizavel)
                .map(PrevisaoPrecoService::precos)
                .toList();
        double[] serie = precos(registros);
        HistoricoPreco ultimo = registros.get(registros.size() - 1);
        BigDecimal ultimoPreco = ultimo.getPreco();

        List<AvaliacaoSemana> avaliacoes = PrevisorPrecos.avaliar(series);
        List<SemanaPrevistaDTO> semanas = new ArrayList<>();
        for (AvaliacaoSemana avaliacao : avaliacoes) {
            RegressaoRidge modelo = PrevisorPrecos.treinar(series, PrevisorPrecos.ULTIMO_INDICE, avaliacao.semana())
                    .orElseThrow(() -> dadosInsuficientes("Não há ingredientes com histórico suficiente para treinar o modelo."));
            double previsto = PrevisorPrecos.prever(modelo, serie, PrevisorPrecos.ULTIMO_INDICE);
            semanas.add(semanaPrevista(avaliacao, previsto, ultimoPreco, ultimo.getDataRegistro()));
        }

        Integer horizonte = PrevisorPrecos.horizonteConfiavel(avaliacoes);
        SemanaPrevistaDTO semanaHorizonte = horizonte == null ? null : semanas.get(horizonte - 1);
        boolean requerAtencao = semanaHorizonte != null
                && semanaHorizonte.variacaoPercentual().compareTo(limiteVariacao) > 0;
        List<ImpactoPratoDTO> impactos = semanaHorizonte == null
                ? List.of()
                : pratoRepository.findDistinctByItensReceita_Ingrediente_Id(ingredienteId).stream()
                        .map(prato -> analiseTrimestralService.calcularImpacto(
                                prato, ingredienteId, ultimoPreco, semanaHorizonte.precoPrevisto()))
                        .toList();

        return new PrevisaoPrecoDTO(
                ingredienteId,
                ingrediente.getNome(),
                ultimoPreco,
                ultimo.getDataRegistro(),
                semanas,
                horizonte,
                limiteVariacao,
                requerAtencao,
                mensagem(ingrediente.getNome(), ultimoPreco, semanaHorizonte, requerAtencao, impactos),
                impactos
        );
    }

    private Map<Long, List<HistoricoPreco>> ultimosRegistrosPorIngrediente() {
        Map<Long, List<HistoricoPreco>> porIngrediente = new LinkedHashMap<>();
        for (HistoricoPreco registro : historicoRepository.findAllByOrderByIngredienteIdAscDataRegistroAsc()) {
            porIngrediente.computeIfAbsent(registro.getIngrediente().getId(), id -> new ArrayList<>()).add(registro);
        }
        porIngrediente.replaceAll((id, lista) -> lista.subList(
                Math.max(0, lista.size() - PrevisorPrecos.REGISTROS_POR_SERIE), lista.size()));
        return porIngrediente;
    }

    private static boolean utilizavel(List<HistoricoPreco> registros) {
        return registros.size() == PrevisorPrecos.REGISTROS_POR_SERIE
                && registros.stream().allMatch(h -> h.getPreco().signum() > 0);
    }

    private static double[] precos(List<HistoricoPreco> registros) {
        return registros.stream().mapToDouble(h -> h.getPreco().doubleValue()).toArray();
    }

    private static SemanaPrevistaDTO semanaPrevista(AvaliacaoSemana avaliacao, double previsto,
                                                    BigDecimal ultimoPreco, LocalDate ultimaData) {
        BigDecimal precoPrevisto = duasCasas(previsto);
        boolean temAvaliacao = avaliacao.previsoes() > 0;
        return new SemanaPrevistaDTO(
                avaliacao.semana(),
                ultimaData.plusWeeks(avaliacao.semana()),
                precoPrevisto,
                temAvaliacao ? duasCasas(Math.max(0, previsto * (1 - avaliacao.erroP80()))) : null,
                temAvaliacao ? duasCasas(previsto * (1 + avaliacao.erroP80())) : null,
                AnaliseTrimestralService.percentual(ultimoPreco, precoPrevisto),
                temAvaliacao ? duasCasas(avaliacao.mapeModelo()) : null,
                temAvaliacao ? duasCasas(avaliacao.mapeReferencia()) : null,
                avaliacao.superouReferencia()
        );
    }

    private static BigDecimal duasCasas(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
    }

    private String mensagem(String nome, BigDecimal ultimoPreco, SemanaPrevistaDTO semana,
                            boolean requerAtencao, List<ImpactoPratoDTO> impactos) {
        if (semana == null) {
            return String.format("Para %s, o modelo não superou a referência simples na primeira semana. "
                    + "A previsão é apenas indicativa e não gera alerta.", nome);
        }
        String pratos = impactos.isEmpty()
                ? "Nenhum prato usa este ingrediente."
                : "Pratos que usam este ingrediente: "
                        + impactos.stream().map(ImpactoPratoDTO::nomePrato).collect(Collectors.joining(", ")) + ".";
        String abertura = requerAtencao ? "Atenção: a previsão" : "A previsão";
        String limite = requerAtencao ? "acima do limite" : "dentro do limite";
        return String.format(PT_BR, "%s para %s é de R$ %.2f em %d semana(s), variação de %.2f%% sobre o último preço de R$ %.2f, %s de %.2f%%. %s",
                abertura, nome, semana.precoPrevisto(), semana.semana(), semana.variacaoPercentual(),
                ultimoPreco, limite, limiteVariacao, pratos);
    }

    private static ResponseStatusException dadosInsuficientes(String motivo) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, motivo);
    }
}
