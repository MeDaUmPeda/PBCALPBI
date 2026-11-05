from datetime import date
from decimal import Decimal

import pytest

import importar_ceagesp as ic


def pagina(data="26/10/2026", linhas="", cabecalho=("Produto", "Classificação", "Uni/Peso", "Menor", "Comum", "Maior", "Quilo")):
    titulos = "".join(f"<td><b>{t}</b></td>" for t in cabecalho)
    return f"""<html><body><form></form>
    <table class="contacao_lista">
      <tr><td colspan="7"><b>Categoria:</b><span> LEGUMES</span>&nbsp;<b>Data:</b><span> {data}</span></td></tr>
      <tr class="h">{titulos}</tr>{linhas}
    </table>
    <table class="cookielawinfo-row-cat-table"><tr><td>Cookie</td></tr></table>
    </body></html>"""


def linha(produto="TOMATE CARMEM ", classificacao="1A", unidade="KG", menor="5,73", comum="6,24", maior="6,74", quilo="1"):
    celulas = (produto, classificacao, unidade, menor, comum, maior, quilo)
    return "<tr>" + "".join(f"<td>{c}</td>" for c in celulas) + "</tr>"


SEM_TABELA = "<html><body><form></form><p>Selecione a data</p></body></html>"


class TestExtrairLinhas:
    def test_le_produto_e_preco_por_kg(self):
        linhas, descartadas = ic.extrair_linhas(pagina(linhas=linha()), date(2026, 10, 26))

        assert descartadas == 0
        assert linhas == [ic.Cotacao("TOMATE CARMEM", "1A", "KG", Decimal("6.24"), Decimal("1"), Decimal("6.24"))]

    def test_converte_maco_e_caixa_pela_coluna_quilo(self):
        html = pagina(linhas=linha("MANJERICÃO", "-", "MC", comum="4,32", quilo="0,15")
                      + linha("OVOS BRANCOS", "EXTRA", "CX", comum="170,75", quilo="22"))

        linhas, _ = ic.extrair_linhas(html, date(2026, 10, 26))

        assert [c.preco_kg for c in linhas] == [Decimal("28.80"), Decimal("7.76")]

    def test_aceita_separador_de_milhar(self):
        linhas, _ = ic.extrair_linhas(pagina(linhas=linha(comum="1.234,50")), date(2026, 10, 26))

        assert linhas[0].preco_comum == Decimal("1234.50")

    @pytest.mark.parametrize("comum, quilo", [("0", "1"), ("", "1"), ("abc", "1"), ("6,24", "0"), ("6,24", "")])
    def test_descarta_valor_invalido(self, comum, quilo):
        linhas, descartadas = ic.extrair_linhas(pagina(linhas=linha(comum=comum, quilo=quilo)), date(2026, 10, 26))

        assert linhas == []
        assert descartadas == 1

    def test_pagina_sem_tabela_significa_dia_sem_cotacao(self):
        assert ic.extrair_linhas(SEM_TABELA, date(2026, 11, 2)) == ([], 0)

    def test_coluna_diferente_e_erro(self):
        html = pagina(linhas=linha(), cabecalho=("Produto", "Tipo", "Uni/Peso", "Menor", "Comum", "Maior", "Quilo"))

        with pytest.raises(ic.ErroImportacao, match="colunas"):
            ic.extrair_linhas(html, date(2026, 10, 26))

    def test_data_diferente_da_pedida_e_erro(self):
        with pytest.raises(ic.ErroImportacao, match="19/10/2026"):
            ic.extrair_linhas(pagina(data="19/10/2026", linhas=linha()), date(2026, 10, 26))


class TestSemanas:
    @pytest.mark.parametrize("fim, ultima", [
        (date(2026, 11, 5), date(2026, 10, 26)),  # quinta: a semana atual não terminou
        (date(2026, 11, 6), date(2026, 10, 26)),  # sexta: o dia ainda não terminou
        (date(2026, 11, 7), date(2026, 11, 2)),  # sábado: a semana já terminou
    ])
    def test_usa_a_ultima_semana_encerrada(self, fim, ultima):
        semanas = ic.semanas_encerradas(fim)

        assert semanas[-1] == ultima

    def test_gera_doze_segundas_consecutivas(self):
        semanas = ic.semanas_encerradas(date(2026, 11, 5))

        assert len(semanas) == 12
        assert semanas[0] == date(2026, 8, 10)
        assert all(s.weekday() == 0 for s in semanas)


class TestBaixarSemana:
    def test_em_feriado_usa_o_proximo_dia_com_cotacao(self):
        pedidas = []

        def consultar(categoria, dia):
            pedidas.append(dia)
            if dia == date(2026, 10, 12):
                return SEM_TABELA
            return pagina(data=dia.strftime("%d/%m/%Y"), linhas=linha())

        dia, linhas, _ = ic.baixar_semana(consultar, "LEGUMES", date(2026, 10, 12), espera=lambda s: None)

        assert dia == date(2026, 10, 13)
        assert pedidas == [date(2026, 10, 12), date(2026, 10, 13)]
        assert len(linhas) == 1

    def test_semana_inteira_sem_cotacao_e_erro(self):
        with pytest.raises(ic.ErroImportacao, match="LEGUMES.*2026-10-12"):
            ic.baixar_semana(lambda c, d: SEM_TABELA, "LEGUMES", date(2026, 10, 12), espera=lambda s: None)

    def test_espera_entre_requisicoes(self):
        esperas = []

        def consultar(categoria, dia):
            return SEM_TABELA if dia == date(2026, 10, 12) else pagina(data=dia.strftime("%d/%m/%Y"), linhas=linha())

        ic.baixar_semana(consultar, "LEGUMES", date(2026, 10, 12), espera=esperas.append)

        assert esperas == [ic.PAUSA_SEGUNDOS, ic.PAUSA_SEGUNDOS]


class TestNome:
    @pytest.mark.parametrize("produto, classificacao, esperado", [
        ("TOMATE CARMEM", "1A", "Tomate Carmem 1A"),
        ("MANJERICÃO", "-", "Manjericão"),
        ("ALHO-PORRO", "TIPO A", "Alho-Porro Tipo A"),
        ("CEBOLA NACIONAL", "CAIXA 3", "Cebola Nacional Caixa 3"),
    ])
    def test_gera_nome_legivel(self, produto, classificacao, esperado):
        assert ic.nome_ingrediente(produto, classificacao) == esperado


def registros(produto, classificacao, semanas=12, categoria="LEGUMES", preco="5.00"):
    inicio = date(2026, 8, 10).toordinal()
    return [
        ic.RegistroCsv(
            semana=date.fromordinal(inicio + 7 * i),
            data_cotacao=date.fromordinal(inicio + 7 * i),
            categoria=categoria, produto=produto, classificacao=classificacao, unidade="KG",
            preco_comum=Decimal(preco), quilo=Decimal("1"), preco_kg=Decimal(preco),
        )
        for i in range(semanas)
    ]


def variedades_completas():
    return [r for produto, classificacao in ic.VARIEDADES for r in registros(produto, classificacao, categoria="DIVERSOS")]


class TestMontarPlano:
    def plano(self, *extras):
        semana_completa = registros("ABOBORA", "-")
        return ic.montar_plano(variedades_completas() + semana_completa + [r for grupo in extras for r in grupo])

    def test_variedades_escolhidas_usam_o_ingrediente_existente(self):
        plano = self.plano()

        tomate = next(s for s in plano.series if s.nome == "Tomate")
        assert tomate.existente is True
        assert len(tomate.registros) == 12

    def test_demais_series_recebem_o_nome_gerado(self):
        plano = self.plano(registros("TOMATE CARMEM", "2A"))

        nova = next(s for s in plano.series if s.nome == "Tomate Carmem 2A")
        assert nova.existente is False

    def test_serie_incompleta_fica_de_fora_e_e_contada(self):
        plano = self.plano(registros("JILO", "1A", semanas=11))

        assert "Jilo 1A" not in [s.nome for s in plano.series]
        assert plano.descartadas == {"Jilo 1A": "série incompleta"}

    def test_variedade_escolhida_incompleta_cancela_tudo(self):
        linhas = [r for r in variedades_completas() if not (r.produto == "TOMATE CARMEM" and r.semana == date(2026, 8, 17))]

        with pytest.raises(ic.ErroImportacao, match="Tomate"):
            ic.montar_plano(linhas)

    def test_nome_de_ingrediente_simulado_nao_e_sobrescrito(self):
        plano = self.plano(registros("SAL", "-"), registros("TOMATE", "-"))

        nomes = [s.nome for s in plano.series]
        assert "Sal" not in nomes
        assert nomes.count("Tomate") == 1
        assert plano.descartadas["Sal"] == "nome reservado"

    def test_linhas_repetidas_na_mesma_semana_descartam_a_serie(self):
        plano = self.plano(registros("JILO", "1A") + registros("JILO", "1A", semanas=1))

        assert plano.descartadas["Jilo 1A"] == "linhas repetidas"

    def test_mesmo_nome_em_duas_categorias_descarta_as_duas(self):
        plano = self.plano(registros("JILO", "1A", categoria="LEGUMES"), registros("JILO", "1A", categoria="VERDURAS"))

        assert "Jilo 1A" not in [s.nome for s in plano.series]
        assert plano.descartadas["Jilo 1A"] == "nome em mais de uma categoria"

    def test_csv_sem_doze_semanas_e_erro(self):
        with pytest.raises(ic.ErroImportacao, match="12 semanas"):
            ic.montar_plano(registros("JILO", "1A", semanas=11))

    def test_ultimo_preco_e_o_da_semana_mais_recente(self):
        primeira = registros("JILO", "1A", preco="1.00")
        primeira[-1].preco_kg = Decimal("9.99")

        plano = self.plano(list(reversed(primeira)))

        assert next(s for s in plano.series if s.nome == "Jilo 1A").ultimo_preco == Decimal("9.99")


class TestCsv:
    def test_grava_e_le_os_mesmos_registros(self, tmp_path):
        arquivo = tmp_path / "ceagesp.csv"
        originais = registros("MANJERICÃO", "-", semanas=2)

        ic.gravar_csv(arquivo, originais)

        assert ic.ler_csv(arquivo) == originais

    def test_falha_ao_gravar_preserva_o_csv_anterior(self, tmp_path, monkeypatch):
        arquivo = tmp_path / "ceagesp.csv"
        ic.gravar_csv(arquivo, registros("JILO", "1A", semanas=1))
        anterior = arquivo.read_bytes()

        def falha(*_):
            raise OSError("disco cheio")

        monkeypatch.setattr(ic.os, "replace", falha)
        with pytest.raises(OSError):
            ic.gravar_csv(arquivo, registros("ABOBORA", "-", semanas=1))

        assert arquivo.read_bytes() == anterior


def test_senha_do_banco_e_obrigatoria(monkeypatch):
    monkeypatch.delenv("PGPASSWORD", raising=False)

    with pytest.raises(ic.ErroImportacao, match="PGPASSWORD"):
        ic.parametros_conexao()
