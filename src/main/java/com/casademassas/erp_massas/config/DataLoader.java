package com.casademassas.erp_massas.config;

import com.casademassas.erp_massas.model.HistoricoPreco;
import com.casademassas.erp_massas.model.Ingrediente;
import com.casademassas.erp_massas.model.ItemReceita;
import com.casademassas.erp_massas.model.Prato;
import com.casademassas.erp_massas.repository.HistoricoPrecoRepository;
import com.casademassas.erp_massas.repository.IngredienteRepository;
import com.casademassas.erp_massas.repository.PratoRepository;
import com.casademassas.erp_massas.service.PrecificacaoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataLoader implements CommandLineRunner {

    private final IngredienteRepository ingredienteRepository;
    private final HistoricoPrecoRepository historicoRepository;
    private final PratoRepository pratoRepository;
    private final PrecificacaoService precificacaoService;
    private final ObjectMapper objectMapper;

    // Construtor explícito para injeção de dependência no Spring Boot
    public DataLoader(IngredienteRepository ingredienteRepository,
                      HistoricoPrecoRepository historicoRepository,
                      PratoRepository pratoRepository,
                      PrecificacaoService precificacaoService,
                      ObjectMapper objectMapper) {
        this.ingredienteRepository = ingredienteRepository;
        this.historicoRepository = historicoRepository;
        this.pratoRepository = pratoRepository;
        this.precificacaoService = precificacaoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        // Se já tiver dados no banco, não executa o carregamento novamente
        if (ingredienteRepository.count() > 0) {
            return;
        }

        // Lê o arquivo JSON diretamente usando JsonNode (muito mais seguro contra erros de sintaxe)
        InputStream inputStream = new ClassPathResource("dados_iniciais.json").getInputStream();
        JsonNode rootNode = objectMapper.readTree(inputStream);

        Map<String, Ingrediente> ingredienteMap = new HashMap<>();

        // 1. Cadastra os Ingredientes
        JsonNode ingredientesNode = rootNode.get("ingredientes");
        for (JsonNode node : ingredientesNode) {
            Ingrediente ing = new Ingrediente();
            ing.setNome(node.get("nome").asText());
            ing.setUnidadeMedida(node.get("unidadeMedida").asText());
            ing.setPrecoCustoAtual(new BigDecimal(node.get("precoCustoAtual").asText()));

            Ingrediente salvo = ingredienteRepository.save(ing);
            ingredienteMap.put(salvo.getNome().toLowerCase(), salvo);
        }

        // 2. Cadastra o Histórico Semanal (12 semanas)
        JsonNode historicoNode = rootNode.get("historicoPrecos");
        for (JsonNode node : historicoNode) {
            String nomeIng = node.get("nomeIngrediente").asText().toLowerCase();
            Ingrediente ing = ingredienteMap.get(nomeIng);

            if (ing != null) {
                HistoricoPreco hp = new HistoricoPreco();
                hp.setIngrediente(ing);
                hp.setPreco(new BigDecimal(node.get("preco").asText()));
                hp.setDataRegistro(LocalDate.parse(node.get("dataRegistro").asText()));

                historicoRepository.save(hp);
            }
        }

        // 3. Cadastra os Pratos e suas Fichas Técnicas
        JsonNode pratosNode = rootNode.get("pratos");
        for (JsonNode pratoNode : pratosNode) {
            Prato prato = new Prato();
            prato.setNome(pratoNode.get("nome").asText());
            prato.setPrecoVendaPraticado(new BigDecimal(pratoNode.get("precoVendaPraticado").asText()));

            List<ItemReceita> itens = new ArrayList<>();
            JsonNode itensNode = pratoNode.get("itensReceita");

            for (JsonNode itemNode : itensNode) {
                String nomeIng = itemNode.get("nomeIngrediente").asText().toLowerCase();
                Ingrediente ing = ingredienteMap.get(nomeIng);

                if (ing != null) {
                    ItemReceita item = new ItemReceita();
                    item.setPrato(prato);
                    item.setIngrediente(ing);
                    item.setQuantidadeUtilizada(new BigDecimal(itemNode.get("quantidadeUtilizada").asText()));
                    itens.add(item);
                }
            }

            prato.setItensReceita(itens);
            precificacaoService.calcularCustoEPrecoSugerido(prato);
            pratoRepository.save(prato);
        }
    }
}