package mx.gob.imss.TestCTG;


import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import mx.gob.imss.TestCTG.cics.beans.InputDataContainer;
import mx.gob.imss.TestCTG.cics.beans.OutputDataContainer;
import mx.gob.imss.TestCTG.remote.ComunicacionCICSBeanRemote;
import mx.gob.imss.TestCTG.services.ComunicacionCICSBean;

public class Test19 implements Serializable {

    private static final long serialVersionUID = -1528629493345218047L; // Cambiado para ser diferente a Test18

    ComunicacionCICSBeanRemote servicioCICS;

    public Test19() {
        servicioCICS = new ComunicacionCICSBean();
    }

    public static void main(String[] args) {
        Test19 test = new Test19();

        System.out.println("--- Iniciando prueba con Channels y Containers ---");

        // Datos para enviar en un container
        InputDataContainer inputData = new InputDataContainer("DATO1", "DATO2", 123);
        String channelName = "MYCHANNEL";
        String inputContainerName = "INPUTCONT"; // Nombre del container de entrada
        String outputContainerName = "OUTPTCONT"; // Nombre del container de salida esperado de CICS
        String cicsProgram = "TESTPROG"; // Nombre del programa CICS que maneja Channels/Containers

        Map<String, byte[]> inputContainers = new HashMap<>();
        try {
            inputContainers.put(inputContainerName, inputData.toBytes());
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error al codificar datos para el container: " + e.getMessage());
            return;
        }

        Map<String, byte[]> responseContainers = test.servicioCICS.enviaReciveConChannelsContainers(
                inputContainers, "USER", "PASS", cicsProgram, channelName);

        if (responseContainers != null && !responseContainers.isEmpty()) {
            System.out.println("\n--- Containers de respuesta recibidos ---");
            byte[] outputBytes = responseContainers.get(outputContainerName);
            if (outputBytes != null) {
                try {
                    OutputDataContainer outputData = new OutputDataContainer(outputBytes);
                    System.out.println("Contenido del Container '" + outputContainerName + "': " + outputData);
                    System.out.println("  Response Field 1: " + outputData.getResponseField1());
                    System.out.println("  Response Field 2: " + outputData.getResponseField2());
                    System.out.println("  Status: " + outputData.getStatus());
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Error al decodificar el container de salida: " + e.getMessage());
                }
            } else {
                System.out.println("No se encontró el container de salida esperado: " + outputContainerName);
                System.out.println("Containers disponibles en la respuesta:");
                for (String name : responseContainers.keySet()) {
                    System.out.println(" - " + name);
                }
            }
        } else {
            System.out.println("No se recibieron containers de respuesta.");
        }

        System.out.println("--- Fin de la prueba con Channels y Containers ---");
    }
}