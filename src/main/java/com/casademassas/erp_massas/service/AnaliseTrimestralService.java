package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.dto.RelatorioReajusteDTO;
import com.casademassas.erp_massas.dto.RelatorioReajusteDTO;
import com.casademassas.erp_massas.model.HistoricoPreco;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnaliseTrimestralService {

    private final HistoricoPrecoRepository historicoRepository;
    private final IngredienteRepository ingredienteRepository;

    public RelatorioReajusteDTO analisarIngrediente(Long ingredienteId) {
        Ingrediente ingrediente = ingredienteRepository.findById(ingredienteId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente não encontrado ID: " + ingredienteId));

        List<HistoricoPreco> historico = historicoRepository.findTop12ByIngredienteIdOrderByDataRegistroDesc(ingredienteId);

        if (historico.size() < 12) {
            throw new IllegalStateException("Histórico insuficiente para cálculo trimestral (mínimo de 12 registros exigidos).");
        }

        BigDecimal precoInicial = historico.get(historico.size() - 1).getPreco();
        BigDecimal somaPrecos = historico.stream()
                .map(HistoricoPreco::getPreco)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal precoMedio = somaPrecos.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        BigDecimal variacaoPercentual = precoMedio.subtract(precoInicial)
                .divide(precoInicial, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        boolean requerReajuste = variacaoPercentual.compareTo(BigDecimal.valueOf(100.0)) > 0;
        BigDecimal percentualReajusteAplicavel = BigDecimal.ZERO;
        String mensagem;

        if (requerReajuste) {
            percentualReajusteAplicavel = variacaoPercentual.subtract(BigDecimal.valueOf(100.0));
            mensagem = String.format("Atenção: O ingrediente %s teve aumento médio de %.2f%% nas últimas 12 semanas. Reajuste sugerido no preço final do prato: %.2f%%.",
                    ingrediente.getNome(), variacaoPercentual, percentualReajusteAplicavel);
        } else {
            mensagem = String.format("O ingrediente %s variou %.2f%% nas últimas 12 semanas. Preço dentro do limite aceitável.",
                    ingrediente.getNome(), variacaoPercentual);
        }

        return new RelatorioReajusteDTO(
                ingrediente.getId(),
                ingrediente.getNome(),
                precoMedio,
                precoInicial,
                variacaoPercentual,
                requerReajuste,
                percentualReajusteAplicavel,
                mensagem
        );
    }
}