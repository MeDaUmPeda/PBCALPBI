package com.casademassas.erp_massas.repository;

import com.casademassas.erp_massas.model.HistoricoPreco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, Long> {
    List<HistoricoPreco> findTop12ByIngredienteIdOrderByDataRegistroDesc(Long ingredienteId);
}