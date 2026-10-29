package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.model.ItemReceita;
import com.casademassas.erp_massas.model.Prato;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class PrecificacaoService {

    private static final BigDecimal MULTIPLICADOR_MARGEM = new BigDecimal("2.00"); // Margem de 100%

    public void calcularCustoEPrecoSugerido(Prato prato) {
        BigDecimal custoTotal = BigDecimal.ZERO;

        if (prato.getItensReceita() != null) {
            for (ItemReceita item : prato.getItensReceita()) {
                BigDecimal precoIngrediente = item.getIngrediente().getPrecoCustoAtual();
                BigDecimal quantidade = item.getQuantidadeUtilizada();
                BigDecimal custoItem = precoIngrediente.multiply(quantidade);
                custoTotal = custoTotal.add(custoItem);
            }
        }

        prato.setCustoTotalCalculado(custoTotal);
        prato.setPrecoVendaSugerido(custoTotal.multiply(MULTIPLICADOR_MARGEM));
    }
}
