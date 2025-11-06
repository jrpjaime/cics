package mx.gob.imss.TestCTG.cics.beans;

import java.io.UnsupportedEncodingException;

public class InputDataContainer {
    private String field1;
    private String field2;
    private int field3;

    public InputDataContainer(String field1, String field2, int field3) {
        this.field1 = field1;
        this.field2 = field2;
        this.field3 = field3;
    }

    public String getField1() {
        return field1;
    }

    public void setField1(String field1) {
        this.field1 = field1;
    }

    public String getField2() {
        return field2;
    }

    public void setField2(String field2) {
        this.field2 = field2;
    }

    public int getField3() {
        return field3;
    }

    public void setField3(int field3) {
        this.field3 = field3;
    }

    // Método para convertir este bean a un byte[] (simulando un formato de datos para CICS)
    public byte[] toBytes() throws UnsupportedEncodingException {
        // En un entorno real, esto debería coincidir con el layout de CICS
        // Para el ejemplo, simplemente concatenamos los campos.
        String data = String.format("%-10s%-10s%05d", field1, field2, field3);
        return data.getBytes("IBM037");
    }
}