package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.dto.HistoricoPrecoDTO;
import com.casademassas.erp_massas.dto.PaginaDTO;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.service.IngredienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/ingredientes")
@CrossOrigin("*")
@RequiredArgsConstructor
public class IngredienteController {

    private static final Set<Integer> TAMANHOS_PAGINA = new TreeSet<>(Set.of(10, 30, 50, 100));

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

    @GetMapping("/pagina")
    public PaginaDTO<Ingrediente> listarPagina(@RequestParam(defaultValue = "") String nome,
                                               @RequestParam(defaultValue = "0") int pagina,
                                               @RequestParam(defaultValue = "10") int tamanho) {
        if (pagina < 0 || !TAMANHOS_PAGINA.contains(tamanho)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use uma página a partir de 0 e um tamanho entre " + TAMANHOS_PAGINA + ".");
        }
        Page<Ingrediente> resultado = ingredienteRepository.findByNomeContainingIgnoreCase(
                nome.trim(), PageRequest.of(pagina, tamanho, Sort.by("nome").ascending()));
        return new PaginaDTO<>(resultado.getContent(), resultado.getTotalElements(), pagina, tamanho);
    }

    @GetMapping("/{id}")
    public Ingrediente buscar(@PathVariable Long id) {
        return ingredienteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingrediente não encontrado ID: " + id));
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
