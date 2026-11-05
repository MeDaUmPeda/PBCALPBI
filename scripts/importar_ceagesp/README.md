# Importação de preços da CEAGESP

Baixa as cotações de atacado da CEAGESP e popula o banco do projeto com 12 semanas de preços em R$/kg.

## Origem dos dados

| Item | Valor |
|---|---|
| Fonte | [CEAGESP: Cotações - Preços no Atacado](https://ceagesp.gov.br/cotacoes/) |
| Local | Entreposto da Capital, São Paulo |
| Categorias | Legumes, Verduras e Diversos |
| Período do CSV atual | Semanas de 10/08/2026 a 26/10/2026, coletadas em 05/11/2026 |
| Arquivo | `dados/ceagesp.csv` |

Na semana com feriado ou sem cotação na segunda-feira, o script usa o próximo dia útil com dados.

## Limites

- **Tipo de preço:** venda no atacado em São Paulo. Não é o preço pago pelo restaurante.
- **Dados mistos:** Ovo, Farinha, Leite, Carne Moída (Músculo), Molho de Tomate, Azeite, Manteiga, Ricota e Sal continuam com preços simulados do seed. O banco não tem um campo para diferenciar os dois casos; este README é o registro.
- **Séries incompletas:** produtos sem cotação nas 12 semanas não são importados.
- **Sinal do site:** o [`robots.txt` da CEAGESP](https://ceagesp.gov.br/robots.txt) traz `ai-train=no`. O projeto segue por decisão da equipe, por ser um TCC sem fins comerciais.

## Instalação

Requer Python 3.11 ou mais novo. No PowerShell, nesta pasta:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

## Uso

**Baixar os dados.** Grava o CSV somente se todas as semanas e categorias tiverem dados. Leva alguns minutos, porque faz uma consulta por vez, com pausa:

```powershell
.\.venv\Scripts\python.exe importar_ceagesp.py baixar
```

**Fazer backup antes de importar:**

```powershell
cmd /c "docker exec casademassas-db pg_dump -U postgres casademassas_db > ..\..\..\backup-antes-ceagesp.sql"
```

**Conferir e importar.** Com `--simular`, o resumo aparece e nada é gravado:

```powershell
$env:PGPASSWORD = "senha_do_banco"
.\.venv\Scripts\python.exe importar_ceagesp.py importar --simular
.\.venv\Scripts\python.exe importar_ceagesp.py importar
```

A conexão usa `PGHOST`, `PGPORT`, `PGDATABASE` e `PGUSER`, com os valores do `docker-compose.yml` como padrão. `PGPASSWORD` é obrigatória.

## O que a importação faz

Tudo acontece em uma única transação: se algo falhar, nada é gravado.

1. **Preço:** usa `Comum / Quilo`, sempre em R$/kg.
2. **Ingredientes existentes:** Tomate (Tomate Carmem 1A), Cebola (Cebola Nacional Caixa 3), Batata (Batata Lavada Especial), Alho (Alho Nacional Tipo 6) e Manjericão recebem preços reais, e o histórico anterior é apagado.
3. **Demais produtos:** viram ingredientes em `KG`, como "Tomate Carmem 2A".
4. **Pratos:** custo e preço sugerido são recalculados. O preço praticado não muda.

Rodar de novo com o mesmo CSV não duplica registros.

## Desfazer

Pare o backend e restaure o backup em um banco vazio:

```powershell
docker exec casademassas-db psql -U postgres -c "DROP DATABASE casademassas_db" -c "CREATE DATABASE casademassas_db"
cmd /c "docker exec -i casademassas-db psql -U postgres -d casademassas_db < ..\..\..\backup-antes-ceagesp.sql"
```

## Testes

```powershell
.\.venv\Scripts\python.exe -m pytest
```

Os testes não usam rede nem banco.
