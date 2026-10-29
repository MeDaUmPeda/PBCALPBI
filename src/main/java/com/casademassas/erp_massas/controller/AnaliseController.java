package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.dto.RelatorioReajusteDTO;
import com.casademassas.erp_massas.service.AnaliseTrimestralService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analise")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AnaliseController {

    private final AnaliseTrimestralService analiseTrimestralService;

    @GetMapping("/trimestral/{ingredienteId}")
    public RelatorioReajusteDTO analisarIngrediente(@PathVariable Long ingredienteId) {
        return analiseTrimestralService.analisarIngrediente(ingredienteId);
    }
}