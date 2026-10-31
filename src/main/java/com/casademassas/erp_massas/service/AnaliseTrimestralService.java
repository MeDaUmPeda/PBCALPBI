package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.dto.ImpactoPratoDTO;
import com.casademassas.erp_massas.dto.RelatorioReajusteDTO;
import com.casademassas.erp_massas.model.HistoricoPreco;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.model.ItemReceita;
import com.casademassas.erp_massas.model.Prato;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AnaliseTrimestralService {

    private final HistoricoPrecoRepository historicoRepository;
    private final IngredienteRepository ingredienteRepository;
    private final PratoRepository pratoRepository;
    private final BigDecimal limiteVariacao;

    public AnaliseTrimestralService(HistoricoPrecoRepository historicoRepository,
                                    IngredienteRepository ingredienteRepository,
                                    PratoRepository pratoRepository,
                                    @Value("${analise.limite-variacao:10}") BigDecimal limiteVariacao) {
        this.historicoRepository = historicoRepository;
        this.ingredienteRepository = ingredienteRepository;
        this.pratoRepository = pratoRepository;
        this.limiteVariacao = limiteVariacao;
    }

    @Transactional(readOnly = true)
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
        BigDecimal variacaoPercentual = percentual(precoInicial, precoMedio);
        boolean requerReajuste = variacaoPercentual.compareTo(limiteVariacao) > 0;

        List<ImpactoPratoDTO> pratosImpactados = pratoRepository.findDistinctByItensReceita_Ingrediente_Id(ingredienteId)
                .stream()
                .map(prato -> calcularImpacto(prato, ingredienteId, precoInicial, precoMedio))
                .toList();

        String mensagem;
        if (requerReajuste) {
            mensagem = String.format("Atenção: o ingrediente %s teve aumento médio de %.2f%% nas últimas 12 semanas, acima do limite de %.2f%%. "
                            + "%d prato(s) utilizam este ingrediente e devem ter o preço revisado.",
                    ingrediente.getNome(), variacaoPercentual, limiteVariacao, pratosImpactados.size());
        } else {
            mensagem = String.format("O ingrediente %s variou %.2f%% nas últimas 12 semanas. Preço dentro do limite aceitável de %.2f%%.",
                    ingrediente.getNome(), variacaoPercentual, limiteVariacao);
        }

        return new RelatorioReajusteDTO(
                ingrediente.getId(),
                ingrediente.getNome(),
                precoMedio,
                precoInicial,
                variacaoPercentual,
                limiteVariacao,
                requerReajuste,
                mensagem,
                pratosImpactados
        );
    }

    /**
     * Recalcula o custo do prato trocando apenas o preço do ingrediente analisado:
     * base = preço no início do período, projetado = preço médio das 12 semanas.
     * Os demais ingredientes usam o preço de custo atual.
     */
    private ImpactoPratoDTO calcularImpacto(Prato prato, Long ingredienteId, BigDecimal precoInicial, BigDecimal precoMedio) {
        BigDecimal custoBase = BigDecimal.ZERO;
        BigDecimal custoProjetado = BigDecimal.ZERO;
        BigDecimal quantidade = BigDecimal.ZERO;

        for (ItemReceita item : prato.getItensReceita()) {
            BigDecimal qtd = item.getQuantidadeUtilizada();
            if (item.getIngrediente().getId().equals(ingredienteId)) {
                quantidade = quantidade.add(qtd);
                custoBase = custoBase.add(precoInicial.multiply(qtd));
                custoProjetado = custoProjetado.add(precoMedio.multiply(qtd));
            } else {
                BigDecimal custoItem = item.getIngrediente().getPrecoCustoAtual().multiply(qtd);
                custoBase = custoBase.add(custoItem);
                custoProjetado = custoProjetado.add(custoItem);
            }
        }

        BigDecimal variacaoCusto = percentual(custoBase, custoProjetado);

        BigDecimal precoAtual = prato.getPrecoVendaPraticado() != null
                ? prato.getPrecoVendaPraticado()
                : prato.getPrecoVendaSugerido();
        BigDecimal precoRecomendado = precoAtual == null ? null : precoAtual
                .multiply(BigDecimal.ONE.add(variacaoCusto.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        return new ImpactoPratoDTO(
                prato.getId(),
                prato.getNome(),
                quantidade,
                custoBase.setScale(2, RoundingMode.HALF_UP),
                custoProjetado.setScale(2, RoundingMode.HALF_UP),
                variacaoCusto,
                precoAtual,
                precoRecomendado
        );
    }

    private static BigDecimal percentual(BigDecimal de, BigDecimal para) {
        if (de.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return para.subtract(de)
                .divide(de, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
