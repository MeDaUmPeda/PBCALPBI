package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.dto.RelatorioReajusteDTO;
import com.casademassas.erp_massas.model.HistoricoPreco;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnaliseTrimestralServiceTest {

    private final HistoricoPrecoRepository historicoRepository = mock(HistoricoPrecoRepository.class);
    private final IngredienteRepository ingredienteRepository = mock(IngredienteRepository.class);
    private final PratoRepository pratoRepository = mock(PratoRepository.class);

    @Test
    void analisarTodosIgnoraIngredienteSemHistoricoSuficiente() {
        Ingrediente completo = ingrediente(1L, "Completo", 12);
        Ingrediente incompleto = ingrediente(2L, "Incompleto", 11);
        when(ingredienteRepository.findAll()).thenReturn(List.of(completo, incompleto));
        when(pratoRepository.findDistinctByItensReceita_Ingrediente_Id(anyLong())).thenReturn(List.of());

        List<RelatorioReajusteDTO> relatorios = new AnaliseTrimestralService(
                historicoRepository, ingredienteRepository, pratoRepository, BigDecimal.TEN).analisarTodos();

        assertThat(relatorios).extracting(RelatorioReajusteDTO::getNomeIngrediente).containsExactly("Completo");
    }

    private Ingrediente ingrediente(Long id, String nome, int registros) {
        Ingrediente ingrediente = new Ingrediente(id, nome, "KG", BigDecimal.ONE);
        List<HistoricoPreco> historico = new ArrayList<>();
        for (int i = 0; i < registros; i++) {
            historico.add(new HistoricoPreco(null, ingrediente, BigDecimal.ONE, LocalDate.of(2026, 8, 1).plusWeeks(i)));
        }
        when(ingredienteRepository.findById(id)).thenReturn(Optional.of(ingrediente));
        when(historicoRepository.findTop12ByIngredienteIdOrderByDataRegistroDesc(id)).thenReturn(historico);
        return ingrediente;
    }
}
