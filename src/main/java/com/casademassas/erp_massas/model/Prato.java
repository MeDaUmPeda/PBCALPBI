package com.casademassas.erp_massas.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "tb_prato")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Prato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @OneToMany(mappedBy = "prato", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ItemReceita> itensReceita;

    private BigDecimal custoTotalCalculado;
    private BigDecimal precoVendaSugerido;
    private BigDecimal precoVendaPraticado;
}