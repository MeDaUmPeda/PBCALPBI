package com.casademassas.erp_massas.controller;

import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.service.IngredienteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngredienteController.class)
class IngredienteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private IngredienteRepository ingredienteRepository;
    @MockBean
    private HistoricoPrecoRepository historicoPrecoRepository;
    @MockBean
    private IngredienteService ingredienteService;

    @Test
    void paginaFiltraPeloNomeSemEspacosEOrdenaPorNome() throws Exception {
        Ingrediente tomate = new Ingrediente(3L, "Tomate", "KG", new BigDecimal("6.24"));
        PageRequest pedido = PageRequest.of(2, 30, Sort.by("nome").ascending());
        when(ingredienteRepository.findByNomeContainingIgnoreCase(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(tomate), pedido, 61));

        mvc.perform(get("/api/ingredientes/pagina").param("nome", " tom ").param("pagina", "2").param("tamanho", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Tomate"))
                .andExpect(jsonPath("$.total").value(61))
                .andExpect(jsonPath("$.pagina").value(2))
                .andExpect(jsonPath("$.tamanho").value(30));

        ArgumentCaptor<Pageable> paginacao = ArgumentCaptor.forClass(Pageable.class);
        verify(ingredienteRepository).findByNomeContainingIgnoreCase(org.mockito.ArgumentMatchers.eq("tom"), paginacao.capture());
        assertThat(paginacao.getValue()).isEqualTo(pedido);
    }

    @Test
    void tamanhoForaDasOpcoesResultaEm400() throws Exception {
        mvc.perform(get("/api/ingredientes/pagina").param("tamanho", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginaNegativaResultaEm400() throws Exception {
        mvc.perform(get("/api/ingredientes/pagina").param("pagina", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscaIngredientePorId() throws Exception {
        when(ingredienteRepository.findById(3L))
                .thenReturn(Optional.of(new Ingrediente(3L, "Tomate", "KG", new BigDecimal("6.24"))));
        when(ingredienteRepository.findById(9L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/ingredientes/3")).andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Tomate"));
        mvc.perform(get("/api/ingredientes/9")).andExpect(status().isNotFound());
    }
}
