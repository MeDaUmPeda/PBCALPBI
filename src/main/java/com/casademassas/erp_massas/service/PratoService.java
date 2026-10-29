package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.model.Prato;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PratoService {

    private final PratoRepository pratoRepository;
    private final IngredienteRepository ingredienteRepository;
    private final PrecificacaoService precificacaoService;

    public Prato cadastrarPrato(Prato prato) {
        if (prato.getItensReceita() != null) {
            prato.getItensReceita().forEach(item -> {
                Ingrediente ingredienteCarregado = ingredienteRepository.findById(item.getIngrediente().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Ingrediente não encontrado ID: " + item.getIngrediente().getId()));
                item.setIngrediente(ingredienteCarregado);
                item.setPrato(prato);
            });
        }

        precificacaoService.calcularCustoEPrecoSugerido(prato);
        return pratoRepository.save(prato);
    }
}