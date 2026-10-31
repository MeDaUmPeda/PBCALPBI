package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.model.Prato;
import com.casademassas.erp_massas.repository.PratoRepository;
import com.casademassas.erp_massas.service.PratoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/pratos")
@CrossOrigin("*")
@RequiredArgsConstructor
public class PratoController {

    private final PratoService pratoService;
    private final PratoRepository pratoRepository;

    @PostMapping
    public Prato criarPrato(@RequestBody Prato prato) {
        return pratoService.cadastrarPrato(prato);
    }

    @GetMapping
    public List<Prato> listarTodos() {
        return pratoRepository.findAll();
    }

    // A ficha técnica (itensReceita) é removida junto por cascade
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        if (!pratoRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prato não encontrado ID: " + id);
        }
        pratoRepository.deleteById(id);
    }
}
