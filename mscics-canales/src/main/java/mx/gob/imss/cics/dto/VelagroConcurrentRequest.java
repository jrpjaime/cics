package mx.gob.imss.cics.dto;

import java.util.List;

import lombok.Data;

@Data
public class VelagroConcurrentRequest {
    private List<VelagroRequest> datosEntradaList; 
}
