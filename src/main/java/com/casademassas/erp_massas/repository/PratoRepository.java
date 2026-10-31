package com.casademassas.erp_massas.repository;

import com.casademassas.erp_massas.model.Prato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PratoRepository extends JpaRepository<Prato, Long> {
    List<Prato> findDistinctByItensReceita_Ingrediente_Id(Long ingredienteId);
}
