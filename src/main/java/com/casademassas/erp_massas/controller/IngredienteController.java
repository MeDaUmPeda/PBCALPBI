package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ingredientes")
@CrossOrigin("*")
@RequiredArgsConstructor
public class IngredienteController {

    private final IngredienteRepository ingredienteRepository;

    @PostMapping
    public Ingrediente criar(@RequestBody Ingrediente ingrediente) {
        return ingredienteRepository.save(ingrediente);
    }

    @GetMapping
    public List<Ingrediente> listarTodos() {
        return ingredienteRepository.findAll();
    }
}
