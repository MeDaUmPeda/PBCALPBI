package com.casademassas.erp_massas.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_ingrediente")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Ingrediente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String unidadeMedida; // Ex: "KG", "UN", "L"

    @Column(nullable = false)
    private BigDecimal precoCustoAtual;
}