package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.dto.HistoricoPrecoDTO;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.service.IngredienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/ingredientes")
@CrossOrigin("*")
@RequiredArgsConstructor
public class IngredienteController {

    private final IngredienteRepository ingredienteRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final IngredienteService ingredienteService;

    @PostMapping
    public Ingrediente criar(@RequestBody Ingrediente ingrediente) {
        return ingredienteRepository.save(ingrediente);
    }

    @GetMapping
    public List<Ingrediente> listarTodos() {
        return ingredienteRepository.findAll();
    }

    // Histórico de preços do ingrediente, do registro mais antigo para o mais recente
    @GetMapping("/{id}/historico")
    public List<HistoricoPrecoDTO> listarHistorico(@PathVariable Long id) {
        if (!ingredienteRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingrediente não encontrado ID: " + id);
        }
        return historicoPrecoRepository.findByIngredienteIdOrderByDataRegistroAsc(id).stream()
                .map(h -> new HistoricoPrecoDTO(h.getDataRegistro(), h.getPreco()))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        ingredienteService.excluir(id);
    }
}
