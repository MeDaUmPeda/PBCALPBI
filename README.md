# Casa de Massas: Backend (ERP Massas)

API REST do TCC **Plataforma Inteligente de Previsão de Variáveis Empresariais** (ESAMC Santos, 2026).

O backend guarda os ingredientes, o histórico semanal de preços e os pratos com ficha técnica. A partir desses dados ele:
- calcula o **custo** e o **preço sugerido** de cada prato;
- faz a **análise trimestral** de preço de cada ingrediente;
- recomenda um **novo preço de venda** para os pratos afetados quando um ingrediente sobe acima do limite.

| | |
|---|---|
| **Linguagem** | Java 17 |
| **Framework** | Spring Boot 3.3.4 (Web, Data JPA, Validation) |
| **Banco de dados** | PostgreSQL 16 (via Docker) |
| **Outros** | Lombok, Hibernate, Maven |
| **Frontend** | Projeto separado: `web-pbcalpbi` (Angular) |

---

## Sumário

1. [Pré-requisitos](#pré-requisitos)
2. [Como rodar](#como-rodar)
3. [Configuração](#configuração)
4. [Arquitetura](#arquitetura)
5. [Modelo de dados](#modelo-de-dados)
6. [Regras de negócio](#regras-de-negócio)
7. [Endpoints da API](#endpoints-da-api)
8. [Carga inicial de dados](#carga-inicial-de-dados)
9. [Problemas comuns](#problemas-comuns)

---

## Pré-requisitos

- **JDK 17 ou 21**, que são as versões suportadas pelo Spring Boot 3.3
- **Docker Desktop**, para o PostgreSQL
- **IntelliJ IDEA**, ou Maven pela linha de comando (o projeto inclui o `mvnw`)

## Como rodar

### 1. Subir o banco

```bash
docker compose up -d
```

O `docker-compose.yml` cria o container `casademassas-db` (PostgreSQL 16) na porta **5432**, com os mesmos dados de acesso do `application.properties`:

| Banco | Usuário | Senha |
|---|---|---|
| `casademassas_db` | `postgres` | `12345` |

Os dados ficam no volume `pgdata` e sobrevivem a reinícios do container.

### 2. Rodar a aplicação

**IntelliJ:** abra o projeto e execute a classe `ErpMassasApplication`.

**Linha de comando:**
```bash
./mvnw spring-boot:run        # Linux/macOS/Git Bash
mvnw.cmd spring-boot:run      # Windows (cmd/PowerShell)
```

A API sobe em **http://localhost:8080**. Para conferir, abra http://localhost:8080/api/ingredientes.

### 3. Encerrar

```bash
docker compose down      # para o banco e mantém os dados
docker compose down -v   # para o banco e APAGA os dados (a carga inicial roda de novo na próxima subida)
```

### Comandos úteis do banco

```bash
# Abrir o terminal SQL
docker exec -it casademassas-db psql -U postgres -d casademassas_db

# Contar registros
docker exec casademassas-db psql -U postgres -d casademassas_db -c "select count(*) from tb_historico_preco;"
```

---

## Configuração

Arquivo `src/main/resources/application.properties`:

| Propriedade | Valor | Descrição |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/casademassas_db` | Conexão com o banco |
| `spring.datasource.username` / `password` | `postgres` / `12345` | Credenciais (as mesmas do `docker-compose.yml`) |
| `spring.jpa.hibernate.ddl-auto` | `update` | O Hibernate cria e atualiza as tabelas a partir das entidades |
| `analise.limite-variacao` | `10` | Variação percentual (%) acima da qual um ingrediente gera alerta de reajuste |
| `server.error.include-message` | `always` | Inclui a mensagem do erro no corpo da resposta, para o frontend exibir |

> Para mudar a sensibilidade do alerta, basta alterar `analise.limite-variacao` e reiniciar a aplicação.

**CORS:** os controllers usam `@CrossOrigin("*")`, e o `CorsConfig` libera `localhost:3000` e `localhost:5173`. O frontend Angular (porta 4200) usa um proxy de desenvolvimento e por isso não depende de CORS.

---

## Arquitetura

Arquitetura em camadas, como descrita no capítulo 5 do TCC:

```
Controller  →  Service  →  Repository  →  PostgreSQL
 (REST/JSON)   (regras)     (Spring Data JPA)
```

```
src/main/java/com/casademassas/erp_massas/
├── ErpMassasApplication.java       # classe principal
├── config/
│   ├── CorsConfig.java             # liberação de CORS
│   └── DataLoader.java             # carga inicial a partir do dados_iniciais.json
├── controller/
│   ├── IngredienteController.java  # /api/ingredientes
│   ├── PratoController.java        # /api/pratos
│   └── AnaliseController.java      # /api/analise
├── service/
│   ├── PrecificacaoService.java    # custo e preço sugerido do prato
│   ├── PratoService.java           # cadastro de prato (carrega ingredientes e precifica)
│   ├── IngredienteService.java     # exclusão de ingrediente (com validação)
│   └── AnaliseTrimestralService.java # análise de 12 semanas e impacto nos pratos
├── repository/                     # interfaces Spring Data JPA
├── model/                          # entidades JPA
└── dto/
    ├── RelatorioReajusteDTO.java   # resposta da análise trimestral
    ├── ImpactoPratoDTO.java        # impacto do ingrediente em um prato
    └── HistoricoPrecoDTO.java      # ponto do histórico (data, preço)
```

---

## Modelo de dados

```
┌──────────────────┐        ┌──────────────────────┐
│  tb_ingrediente  │1──────N│  tb_historico_preco  │
│──────────────────│        │──────────────────────│
│ id               │        │ id                   │
│ nome             │        │ ingrediente_id (FK)  │
│ unidade_medida   │        │ preco                │
│ preco_custo_atual│        │ data_registro        │
└──────────────────┘        └──────────────────────┘
         │1
         │
         │N
┌──────────────────────┐        ┌───────────────────────────┐
│   tb_item_receita    │N──────1│         tb_prato          │
│──────────────────────│        │───────────────────────────│
│ id                   │        │ id                        │
│ prato_id (FK)        │        │ nome                      │
│ ingrediente_id (FK)  │        │ custo_total_calculado     │
│ quantidade_utilizada │        │ preco_venda_sugerido      │
└──────────────────────┘        │ preco_venda_praticado     │
                                └───────────────────────────┘
```

| Entidade | Descrição |
|---|---|
| **Ingrediente** | Insumo com unidade de medida (KG, L, UN, DUZIA…) e preço de custo atual |
| **HistoricoPreco** | Preço do ingrediente em uma data (um registro por semana) |
| **Prato** | Item do cardápio com custo calculado, preço sugerido e preço praticado |
| **ItemReceita** | Linha da ficha técnica: quanto de cada ingrediente o prato usa |

A relação `Prato → ItemReceita` usa `cascade = ALL` e `orphanRemoval = true`: salvar ou excluir um prato salva ou exclui também a ficha técnica dele.

---

## Regras de negócio

### Precificação do prato (`PrecificacaoService`)

Executada ao cadastrar um prato e na carga inicial:
```
custo do prato  = Σ (preço de custo atual do ingrediente × quantidade utilizada)
preço sugerido  = custo do prato × 2        (margem de 100% sobre o custo)
```

### Análise trimestral (`AnaliseTrimestralService`)

Usa os **12 registros mais recentes** do histórico do ingrediente:
```
preço inicial = o mais antigo dos 12 registros
preço médio   = média dos 12 registros
variação (%)  = (preço médio − preço inicial) / preço inicial × 100
requer reajuste = variação > analise.limite-variacao
```
Se o ingrediente tiver menos de 12 registros, a análise não é feita e a API devolve erro.

### Impacto nos pratos

Para **cada prato** que usa o ingrediente analisado, o custo é recalculado trocando **apenas** o preço desse ingrediente. Os demais ingredientes ficam com o preço de custo atual:
```
custo base         = custo do prato usando o preço inicial do período
custo projetado    = custo do prato usando o preço médio das 12 semanas
variação do custo  = (projetado − base) / base × 100
preço recomendado  = preço praticado × (1 + variação do custo / 100)
```
Aplicar ao preço a mesma variação percentual do custo **mantém a margem** do prato. Se o prato não tiver preço praticado, o cálculo usa o preço sugerido.

### Exclusão de ingrediente (`IngredienteService`)

- O histórico de preços do ingrediente é apagado junto.
- A exclusão é **bloqueada (409)** se o ingrediente estiver na ficha técnica de algum prato. A mensagem lista quais pratos o usam.

---

## Endpoints da API

Base: `http://localhost:8080`

### Ingredientes

| Método | Rota | Descrição | Respostas |
|---|---|---|---|
| `GET` | `/api/ingredientes` | Lista todos os ingredientes | 200 |
| `POST` | `/api/ingredientes` | Cadastra um ingrediente | 200 |
| `DELETE` | `/api/ingredientes/{id}` | Exclui o ingrediente e o histórico dele | 204, 404, **409** se usado em prato |
| `GET` | `/api/ingredientes/{id}/historico` | Histórico de preços, do mais antigo ao mais recente | 200, 404 |

### Pratos

| Método | Rota | Descrição | Respostas |
|---|---|---|---|
| `GET` | `/api/pratos` | Lista os pratos com ficha técnica | 200 |
| `POST` | `/api/pratos` | Cadastra um prato (custo e preço sugerido são calculados) | 200, 500 se o ingrediente não existir |
| `DELETE` | `/api/pratos/{id}` | Exclui o prato e a ficha técnica | 204, 404 |

### Análise

| Método | Rota | Descrição | Respostas |
|---|---|---|---|
| `GET` | `/api/analise/trimestral/{ingredienteId}` | Análise de 12 semanas e impacto nos pratos | 200, 500 se houver histórico insuficiente |

### Exemplos

<details>
<summary><code>POST /api/ingredientes</code></summary>

```json
{ "nome": "Queijo Parmesão", "unidadeMedida": "KG", "precoCustoAtual": 89.90 }
```
</details>

<details>
<summary><code>POST /api/pratos</code></summary>

Os ingredientes são referenciados apenas pelo `id`. `custoTotalCalculado` e `precoVendaSugerido` são preenchidos pelo backend.
```json
{
  "nome": "Nhoque ao Sugo",
  "precoVendaPraticado": 32.00,
  "itensReceita": [
    { "ingrediente": { "id": 7 }, "quantidadeUtilizada": 0.300 },
    { "ingrediente": { "id": 10 }, "quantidadeUtilizada": 0.150 }
  ]
}
```
</details>

<details>
<summary><code>GET /api/ingredientes/1/historico</code></summary>

```json
[
  { "dataRegistro": "2026-08-01", "preco": 5.80 },
  { "dataRegistro": "2026-08-08", "preco": 5.85 },
  { "dataRegistro": "2026-08-15", "preco": 5.90 }
]
```
</details>

<details>
<summary><code>GET /api/analise/trimestral/3</code></summary>

```json
{
  "ingredienteId": 3,
  "nomeIngrediente": "Tomate",
  "precoMedio12Semanas": 10.01,
  "precoInicialPeriodo": 4.50,
  "variacaoPercentual": 122.44,
  "limiteVariacao": 10,
  "requerReajuste": true,
  "mensagemAnalise": "Atenção: o ingrediente Tomate teve aumento médio de 122,44% nas últimas 12 semanas, acima do limite de 10,00%. 1 prato(s) utilizam este ingrediente e devem ter o preço revisado.",
  "pratosImpactados": [
    {
      "pratoId": 1,
      "nomePrato": "Macarrão ao Molho de Tomate",
      "quantidadeUtilizada": 1.000,
      "custoBase": 9.50,
      "custoProjetado": 15.01,
      "variacaoCustoPercentual": 58.00,
      "precoVendaPraticado": 30.00,
      "precoVendaRecomendado": 47.40
    }
  ]
}
```
*(valores ilustrativos)*
</details>

<details>
<summary>Erro 409 ao excluir ingrediente em uso</summary>

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Ingrediente usado na ficha técnica de: Macarrão ao Molho de Tomate. Remova-o desses pratos antes de excluir.",
  "path": "/api/ingredientes/1"
}
```
</details>

---

## Carga inicial de dados

O `DataLoader` roda na inicialização e lê `src/main/resources/dados_iniciais.json` **somente se a tabela de ingredientes estiver vazia**. Ele carrega:

| Conteúdo | Quantidade |
|---|---|
| Ingredientes | 14 |
| Histórico de preços | 12 semanas por ingrediente (168 registros) |
| Pratos com ficha técnica | 3 (já precificados) |

Com o limite padrão de 10%, os dados iniciais geram alerta de reajuste para **Ovo, Tomate, Cebola e Batata**.

Para recarregar do zero: `docker compose down -v`, depois `docker compose up -d` e reinicie a aplicação.

---

## Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Connection to localhost:5432 refused` | Banco não está rodando | Abra o Docker Desktop e rode `docker compose up -d` |
| `password authentication failed` | Outro PostgreSQL instalado na máquina está usando a porta 5432 | Pare o serviço do PostgreSQL local, ou mude a porta no `docker-compose.yml` (ex.: `"5433:5432"`) e no `spring.datasource.url` |
| `java: ExceptionInInitializerError ... TypeTag :: UNKNOWN` | Lombok incompatível com JDK 24+ | Use JDK 17 ou 21 no IntelliJ, ou adicione `<lombok.version>1.18.42</lombok.version>` em `<properties>` no `pom.xml` |
| Lombok não gera getters/setters no IntelliJ | Annotation processing desativado | *Settings → Build → Compiler → Annotation Processors → Enable annotation processing* |
| Análise retorna 500 | Ingrediente com menos de 12 registros de histórico | Esperado para ingredientes cadastrados manualmente |
| Endpoint novo retorna 404 | Aplicação rodando com código antigo | Pare e rode de novo a aplicação |
| Dados iniciais não carregaram | A tabela de ingredientes já tinha registros | `docker compose down -v` para limpar o banco |
