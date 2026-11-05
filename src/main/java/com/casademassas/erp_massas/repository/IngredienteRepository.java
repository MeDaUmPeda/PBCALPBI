package com.casademassas.erp_massas.repository;

import com.casademassas.erp_massas.model.Ingrediente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredienteRepository extends JpaRepository<Ingrediente, Long> {

    /** Equivale a LIKE '%nome%', sem diferenciar maiúsculas de minúsculas. */
    Page<Ingrediente> findByNomeContainingIgnoreCase(String nome, Pageable paginacao);
}
