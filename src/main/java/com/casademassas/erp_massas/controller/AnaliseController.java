package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.dto.ExplicacaoPrevisaoDTO;
import com.casademassas.erp_massas.dto.PrevisaoPrecoDTO;
import com.casademassas.erp_massas.dto.RelatorioReajusteDTO;
import com.casademassas.erp_massas.service.AnaliseTrimestralService;
import com.casademassas.erp_massas.service.ExplicacaoPrevisaoService;
import com.casademassas.erp_massas.service.PrevisaoPrecoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analise")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AnaliseController {

    private final AnaliseTrimestralService analiseTrimestralService;
    private final PrevisaoPrecoService previsaoPrecoService;
    private final ExplicacaoPrevisaoService explicacaoPrevisaoService;

    @GetMapping("/trimestral/{ingredienteId}")
    public RelatorioReajusteDTO analisarIngrediente(@PathVariable Long ingredienteId) {
        return analiseTrimestralService.analisarIngrediente(ingredienteId);
    }

    @GetMapping("/previsao/{ingredienteId}")
    public PrevisaoPrecoDTO preverPreco(@PathVariable Long ingredienteId) {
        return previsaoPrecoService.prever(ingredienteId);
    }

    @GetMapping("/previsao/{ingredienteId}/explicacao")
    public ExplicacaoPrevisaoDTO explicarPrevisao(@PathVariable Long ingredienteId) {
        return explicacaoPrevisaoService.explicar(ingredienteId);
    }
}
