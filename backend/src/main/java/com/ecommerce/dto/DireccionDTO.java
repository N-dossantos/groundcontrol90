package com.ecommerce.dto;

import com.ecommerce.entity.Direccion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionDTO {
    private Long id;
    private String calle;
    private String numero;
    private String ciudad;
    private String provincia;
    private String codigoPostal;
    private String pais;
    private boolean esPredeterminada;

    public DireccionDTO(Direccion direccion) {
        this.id = direccion.getId();
        this.calle = direccion.getCalle();
        this.numero = direccion.getNumero();
        this.ciudad = direccion.getCiudad();
        this.provincia = direccion.getProvincia();
        this.codigoPostal = direccion.getCodigoPostal();
        this.pais = direccion.getPais();
        this.esPredeterminada = direccion.isEsPredeterminada();
    }
}
