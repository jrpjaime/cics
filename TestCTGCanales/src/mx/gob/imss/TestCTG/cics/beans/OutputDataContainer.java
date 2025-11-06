package mx.gob.imss.TestCTG.cics.beans;

import java.io.UnsupportedEncodingException;

public class OutputDataContainer {
    private String responseField1;
    private String responseField2;
    private String status;

    public OutputDataContainer(byte[] cicsResponseBytes) throws UnsupportedEncodingException {
        // Aquí deberías parsear los bytes de respuesta de CICS
        // y asignarlos a los campos correspondientes.
        // Para este ejemplo, asumimos un formato simple de 25 caracteres:
        // 10 para responseField1, 10 para responseField2, 5 para status.
        String cicsResponse = new String(cicsResponseBytes, "IBM037");
        if (cicsResponse.length() >= 25) {
            this.responseField1 = cicsResponse.substring(0, 10).trim();
            this.responseField2 = cicsResponse.substring(10, 20).trim();
            this.status = cicsResponse.substring(20, 25).trim();
        } else {
            this.responseField1 = "ERROR";
            this.responseField2 = "ERROR";
            this.status = "ERROR";
        }
    }

    public String getResponseField1() {
        return responseField1;
    }

    public void setResponseField1(String responseField1) {
        this.responseField1 = responseField1;
    }

    public String getResponseField2() {
        return responseField2;
    }

    public void setResponseField2(String responseField2) {
        this.responseField2 = responseField2;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "OutputDataContainer [responseField1=" + responseField1 + ", responseField2=" + responseField2 + ", status=" + status + "]";
    }
}