package com.casademassas.erp_massas.service;

import com.casademassas.erp_massas.model.Prato;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredienteService {

    private final IngredienteRepository ingredienteRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final PratoRepository pratoRepository;

    /**
     * Exclui o ingrediente e seu histórico de preços.
     * Bloqueia a exclusão se o ingrediente fizer parte da ficha técnica de algum prato.
     */
    @Transactional
    public void excluir(Long id) {
        if (!ingredienteRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingrediente não encontrado ID: " + id);
        }

        List<Prato> pratosQueUsam = pratoRepository.findDistinctByItensReceita_Ingrediente_Id(id);
        if (!pratosQueUsam.isEmpty()) {
            String nomes = pratosQueUsam.stream().map(Prato::getNome).collect(Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ingrediente usado na ficha técnica de: " + nomes + ". Remova-o desses pratos antes de excluir.");
        }

        historicoPrecoRepository.deleteByIngredienteId(id);
        ingredienteRepository.deleteById(id);
    }
}
