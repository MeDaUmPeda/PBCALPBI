package com.casademassas.erp_massas.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_item_receita")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ItemReceita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "prato_id")
    @JsonBackReference
    private Prato prato;

    @ManyToOne
    @JoinColumn(name = "ingrediente_id", nullable = false)
    private Ingrediente ingrediente;

    @Column(nullable = false)
    private BigDecimal quantidadeUtilizada; // Ex: 0.200 para 200g
}