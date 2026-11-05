"""Baixa cotações da CEAGESP para um CSV e importa esse CSV no banco do PBCALPBI.

Uso:
    python importar_ceagesp.py baixar [--fim AAAA-MM-DD]
    python importar_ceagesp.py importar [--simular]

Veja README.md para instalação, origem e limites dos dados.
"""
import argparse
import csv
import os
import re
import sys
import time
from collections import defaultdict
from dataclasses import dataclass, field, fields
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal, InvalidOperation
from pathlib import Path

import psycopg
import requests
from bs4 import BeautifulSoup

URL_COTACOES = "https://ceagesp.gov.br/cotacoes/"
USER_AGENT = "pbcalpbi-tcc"
CATEGORIAS = ("LEGUMES", "VERDURAS", "DIVERSOS")
SEMANAS = 12
DIAS_UTEIS = 5
PAUSA_SEGUNDOS = 2
# O site chegou a levar 60 s para responder em um feriado
TIMEOUT_SEGUNDOS = 90
COLUNAS = ["Produto", "Classificação", "Uni/Peso", "Menor", "Comum", "Maior", "Quilo"]
CSV_PADRAO = Path(__file__).parent / "dados" / "ceagesp.csv"

# (produto, classificação) na CEAGESP -> ingrediente que já existe no banco
VARIEDADES = {
    ("TOMATE CARMEM", "1A"): "Tomate",
    ("CEBOLA NACIONAL", "CAIXA 3"): "Cebola",
    ("BATATA LAVADA", "ESPECIAL"): "Batata",
    ("ALHO NACIONAL", "TIPO 6"): "Alho",
    ("MANJERICÃO", "-"): "Manjericão",
}
# Nomes do seed: outra linha da CEAGESP não pode gravar por cima destes ingredientes
NOMES_RESERVADOS = {
    nome.casefold()
    for nome in (
        "Farinha", "Ovo", "Tomate", "Alho", "Cebola", "Manjericão", "Batata", "Leite",
        "Carne Moída (Músculo)", "Molho de Tomate", "Azeite", "Manteiga", "Ricota", "Sal",
    )
}


class ErroImportacao(Exception):
    """Erro esperado, com mensagem pronta para mostrar ao usuário."""


@dataclass(frozen=True)
class Cotacao:
    produto: str
    classificacao: str
    unidade: str
    preco_comum: Decimal
    quilo: Decimal
    preco_kg: Decimal


@dataclass
class RegistroCsv:
    semana: date
    data_cotacao: date
    categoria: str
    produto: str
    classificacao: str
    unidade: str
    preco_comum: Decimal
    quilo: Decimal
    preco_kg: Decimal


@dataclass
class Serie:
    nome: str
    existente: bool
    registros: list[RegistroCsv]

    @property
    def ultimo_preco(self) -> Decimal:
        return self.registros[-1].preco_kg


@dataclass
class Plano:
    series: list[Serie] = field(default_factory=list)
    descartadas: dict[str, str] = field(default_factory=dict)


def _decimal_positivo(texto: str) -> Decimal | None:
    limpo = texto.strip()
    if "," in limpo:
        limpo = limpo.replace(".", "").replace(",", ".")
    try:
        valor = Decimal(limpo)
    except InvalidOperation:
        return None
    return valor if valor.is_finite() and valor > 0 else None


def extrair_linhas(html: str | bytes, dia: date) -> tuple[list[Cotacao], int]:
    """Lê a tabela de cotações. Sem a tabela, o dia não teve cotação."""
    sopa = BeautifulSoup(html, "html.parser")
    tabela = sopa.find("table", class_="contacao_lista")
    if tabela is None:
        return [], 0

    data_tabela = re.search(r"Data:\s*(\d{2}/\d{2}/\d{4})", tabela.get_text(" "))
    if data_tabela is None or data_tabela.group(1) != dia.strftime("%d/%m/%Y"):
        encontrada = data_tabela.group(1) if data_tabela else "ausente"
        raise ErroImportacao(f"A página de {dia} trouxe a data {encontrada}.")

    cabecalho = tabela.find("tr", class_="h")
    titulos = [c.get_text(strip=True) for c in cabecalho.find_all("td")] if cabecalho else []
    if titulos != COLUNAS:
        raise ErroImportacao(f"As colunas da tabela de {dia} mudaram: {titulos}.")

    cotacoes, descartadas = [], 0
    for tr in tabela.find_all("tr"):
        if tr is cabecalho:
            continue
        celulas = [c.get_text(strip=True) for c in tr.find_all("td")]
        if len(celulas) != len(COLUNAS):
            continue
        produto, classificacao, unidade, _, comum, _, quilo = celulas
        preco_comum = _decimal_positivo(comum)
        peso = _decimal_positivo(quilo)
        if preco_comum is None or peso is None:
            descartadas += 1
            continue
        preco_kg = (preco_comum / peso).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
        if preco_kg <= 0:
            descartadas += 1
            continue
        cotacoes.append(Cotacao(produto, classificacao, unidade, preco_comum, peso, preco_kg))
    return cotacoes, descartadas


def semanas_encerradas(fim: date, quantidade: int = SEMANAS) -> list[date]:
    """Segundas-feiras das últimas semanas úteis que terminaram antes de `fim`."""
    segunda = fim - timedelta(days=fim.weekday())
    if segunda + timedelta(days=DIAS_UTEIS - 1) >= fim:
        segunda -= timedelta(weeks=1)
    return [segunda - timedelta(weeks=i) for i in range(quantidade - 1, -1, -1)]


def baixar_semana(consultar, categoria: str, segunda: date, espera=time.sleep) -> tuple[date, list[Cotacao], int]:
    """Consulta de segunda a sexta e usa o primeiro dia com linhas na tabela."""
    for deslocamento in range(DIAS_UTEIS):
        dia = segunda + timedelta(days=deslocamento)
        html = consultar(categoria, dia)
        espera(PAUSA_SEGUNDOS)
        cotacoes, descartadas = extrair_linhas(html, dia)
        if cotacoes:
            return dia, cotacoes, descartadas
    raise ErroImportacao(f"Sem cotação em {categoria} na semana de {segunda}.")


def nome_ingrediente(produto: str, classificacao: str) -> str:
    partes = [produto.strip()]
    if classificacao.strip() not in ("", "-"):
        partes.append(classificacao.strip())
    return " ".join(partes).title()


def montar_plano(registros: list[RegistroCsv]) -> Plano:
    """Mantém as séries completas e decide o ingrediente de cada uma."""
    semanas = {r.semana for r in registros}
    if len(semanas) != SEMANAS:
        raise ErroImportacao(f"O CSV precisa ter {SEMANAS} semanas, mas tem {len(semanas)}.")

    grupos: dict[tuple[str, str, str], list[RegistroCsv]] = defaultdict(list)
    for registro in registros:
        grupos[(registro.categoria, registro.produto, registro.classificacao)].append(registro)

    categorias_por_nome: dict[str, set[str]] = defaultdict(set)
    for categoria, produto, classificacao in grupos:
        categorias_por_nome[nome_ingrediente(produto, classificacao)].add(categoria)

    plano = Plano()
    variedades_encontradas = set()
    for (_, produto, classificacao), grupo in grupos.items():
        ordenados = sorted(grupo, key=lambda r: r.semana)
        completa = len(ordenados) == SEMANAS and {r.semana for r in ordenados} == semanas
        variedade = VARIEDADES.get((produto, classificacao))
        if variedade is not None:
            if not completa:
                raise ErroImportacao(f"A série de {variedade} ({produto} {classificacao}) está incompleta.")
            variedades_encontradas.add(variedade)
            plano.series.append(Serie(variedade, True, ordenados))
            continue

        nome = nome_ingrediente(produto, classificacao)
        if nome.casefold() in NOMES_RESERVADOS:
            plano.descartadas[nome] = "nome reservado"
        elif len(categorias_por_nome[nome]) > 1:
            plano.descartadas[nome] = "nome em mais de uma categoria"
        elif len({r.semana for r in ordenados}) != len(ordenados):
            plano.descartadas[nome] = "linhas repetidas"
        elif not completa:
            plano.descartadas[nome] = "série incompleta"
        else:
            plano.series.append(Serie(nome, False, ordenados))

    ausentes = set(VARIEDADES.values()) - variedades_encontradas
    if ausentes:
        raise ErroImportacao(f"Variedades ausentes no CSV: {', '.join(sorted(ausentes))}.")
    return plano


def gravar_csv(caminho: Path, registros: list[RegistroCsv]) -> None:
    """Grava em arquivo temporário e troca no final, para nunca deixar um CSV pela metade."""
    caminho.parent.mkdir(parents=True, exist_ok=True)
    temporario = caminho.with_suffix(".tmp")
    nomes = [f.name for f in fields(RegistroCsv)]
    with open(temporario, "w", newline="", encoding="utf-8") as arquivo:
        escritor = csv.DictWriter(arquivo, fieldnames=nomes)
        escritor.writeheader()
        for registro in registros:
            escritor.writerow({nome: str(getattr(registro, nome)) for nome in nomes})
    os.replace(temporario, caminho)


def ler_csv(caminho: Path) -> list[RegistroCsv]:
    if not caminho.exists():
        raise ErroImportacao(f"CSV não encontrado: {caminho}. Rode o comando baixar primeiro.")
    with open(caminho, newline="", encoding="utf-8") as arquivo:
        return [
            RegistroCsv(
                semana=date.fromisoformat(linha["semana"]),
                data_cotacao=date.fromisoformat(linha["data_cotacao"]),
                categoria=linha["categoria"],
                produto=linha["produto"],
                classificacao=linha["classificacao"],
                unidade=linha["unidade"],
                preco_comum=Decimal(linha["preco_comum"]),
                quilo=Decimal(linha["quilo"]),
                preco_kg=Decimal(linha["preco_kg"]),
            )
            for linha in csv.DictReader(arquivo)
        ]


def parametros_conexao() -> dict[str, str]:
    senha = os.environ.get("PGPASSWORD")
    if not senha:
        raise ErroImportacao("Defina a variável de ambiente PGPASSWORD com a senha do banco.")
    return {
        "host": os.environ.get("PGHOST", "localhost"),
        "port": os.environ.get("PGPORT", "5432"),
        "dbname": os.environ.get("PGDATABASE", "casademassas_db"),
        "user": os.environ.get("PGUSER", "postgres"),
        "password": senha,
    }


def _consultar_site(sessao: requests.Session):
    def consultar(categoria: str, dia: date) -> bytes:
        try:
            resposta = sessao.post(
                URL_COTACOES,
                data={"cot_grupo": categoria, "cot_data": dia.strftime("%d/%m/%Y")},
                timeout=TIMEOUT_SEGUNDOS,
            )
        except requests.RequestException as erro:
            raise ErroImportacao(f"Falha ao consultar {categoria} em {dia}: {type(erro).__name__}.") from erro
        if resposta.status_code != 200:
            raise ErroImportacao(f"O site respondeu {resposta.status_code} para {categoria} em {dia}.")
        return resposta.content

    return consultar


def comando_baixar(fim: date, caminho: Path) -> None:
    sessao = requests.Session()
    sessao.headers["User-Agent"] = USER_AGENT
    consultar = _consultar_site(sessao)

    registros, descartadas = [], 0
    for segunda in semanas_encerradas(fim):
        for categoria in CATEGORIAS:
            dia, cotacoes, invalidas = baixar_semana(consultar, categoria, segunda)
            descartadas += invalidas
            print(f"{segunda} {categoria}: {len(cotacoes)} linhas em {dia}", flush=True)
            registros.extend(
                RegistroCsv(segunda, dia, categoria, c.produto, c.classificacao, c.unidade,
                            c.preco_comum, c.quilo, c.preco_kg)
                for c in cotacoes
            )
    gravar_csv(caminho, registros)
    print(f"CSV gravado em {caminho}: {len(registros)} linhas; {descartadas} descartadas por valor inválido.")


def _ingrediente_id(cursor, nome: str) -> int | None:
    cursor.execute("SELECT id FROM tb_ingrediente WHERE lower(nome) = lower(%s)", (nome,))
    linhas = cursor.fetchall()
    if len(linhas) > 1:
        raise ErroImportacao(f"Há mais de um ingrediente chamado {nome}.")
    return linhas[0][0] if linhas else None


def aplicar_plano(conexao, plano: Plano) -> dict[str, int]:
    """Aplica o plano na transação aberta. Quem chama decide entre commit e rollback."""
    resumo = {"criados": 0, "atualizados": 0, "registros": 0, "pratos": 0}
    with conexao.cursor() as cursor:
        for serie in plano.series:
            ingrediente = _ingrediente_id(cursor, serie.nome)
            if ingrediente is None and serie.existente:
                raise ErroImportacao(f"O ingrediente {serie.nome} não existe no banco.")
            if ingrediente is None:
                cursor.execute(
                    "INSERT INTO tb_ingrediente (nome, unidade_medida, preco_custo_atual) "
                    "VALUES (%s, 'KG', %s) RETURNING id",
                    (serie.nome, serie.ultimo_preco),
                )
                ingrediente = cursor.fetchone()[0]
                resumo["criados"] += 1
            else:
                cursor.execute(
                    "UPDATE tb_ingrediente SET preco_custo_atual = %s WHERE id = %s",
                    (serie.ultimo_preco, ingrediente),
                )
                resumo["atualizados"] += 1

            cursor.execute("DELETE FROM tb_historico_preco WHERE ingrediente_id = %s", (ingrediente,))
            cursor.executemany(
                "INSERT INTO tb_historico_preco (ingrediente_id, preco, data_registro) VALUES (%s, %s, %s)",
                [(ingrediente, r.preco_kg, r.data_cotacao) for r in serie.registros],
            )
            resumo["registros"] += len(serie.registros)

        # Mesma regra de PrecificacaoService: custo = soma de preço × quantidade; sugerido = custo × 2
        cursor.execute(
            """
            UPDATE tb_prato p
               SET custo_total_calculado = c.custo,
                   preco_venda_sugerido = c.custo * 2
              FROM (SELECT r.prato_id, SUM(i.preco_custo_atual * r.quantidade_utilizada) AS custo
                      FROM tb_item_receita r
                      JOIN tb_ingrediente i ON i.id = r.ingrediente_id
                     GROUP BY r.prato_id) c
             WHERE p.id = c.prato_id
            """
        )
        resumo["pratos"] = cursor.rowcount
    return resumo


def comando_importar(simular: bool, caminho: Path) -> None:
    plano = montar_plano(ler_csv(caminho))
    parametros = parametros_conexao()
    try:
        with psycopg.connect(**parametros) as conexao:
            resumo = aplicar_plano(conexao, plano)
            if simular:
                conexao.rollback()
            else:
                conexao.commit()
    except psycopg.Error as erro:
        # Só o tipo do erro: a mensagem de conexão pode trazer dados do ambiente
        raise ErroImportacao(f"Falha no banco, nada foi gravado: {type(erro).__name__}.") from erro

    print("SIMULAÇÃO: nada foi gravado." if simular else "Importação concluída.")
    print(f"Ingredientes criados: {resumo['criados']}")
    print(f"Ingredientes atualizados: {resumo['atualizados']}")
    print(f"Registros de preço inseridos: {resumo['registros']}")
    print(f"Pratos recalculados: {resumo['pratos']}")
    print(f"Séries descartadas: {len(plano.descartadas)}")
    for nome, motivo in sorted(plano.descartadas.items()):
        print(f"  - {nome}: {motivo}")


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    comandos = parser.add_subparsers(dest="comando", required=True)
    baixar = comandos.add_parser("baixar", help="baixa as cotações para o CSV")
    baixar.add_argument("--fim", type=date.fromisoformat, default=date.today(), help="AAAA-MM-DD (padrão: hoje)")
    importar = comandos.add_parser("importar", help="importa o CSV no banco")
    importar.add_argument("--simular", action="store_true", help="mostra o resumo e não grava nada")
    argumentos = parser.parse_args()

    try:
        if argumentos.comando == "baixar":
            comando_baixar(argumentos.fim, CSV_PADRAO)
        else:
            comando_importar(argumentos.simular, CSV_PADRAO)
    except ErroImportacao as erro:
        print(f"Erro: {erro}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
