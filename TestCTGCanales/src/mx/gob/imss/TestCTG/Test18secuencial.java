package mx.gob.imss.TestCTG;

import java.io.Serializable;
import mx.gob.imss.TestCTG.services.ComunicacionCICSBean;
import mx.gob.imss.TestCTG.remote.ComunicacionCICSBeanRemote;

public class Test18secuencial implements Serializable {

    private static final long serialVersionUID = -1528629493345218046L;

    ComunicacionCICSBeanRemote servicioCICS;

    public Test18secuencial() {
        // Inicialización de tu servicio de CICS
        servicioCICS = new ComunicacionCICSBean();
    }

    public static void main(String[] args) {

        Test18 test = new Test18();
        String respuesta = null;
        int numPeticiones = 1000; // Total de ciclos de consulta a ejecutar

        System.out.println("Iniciando " + numPeticiones + " peticiones secuenciales completas a CICS...");
        System.out.println("--------------------------------------------------");

        // Bucle externo para simular 1000 peticiones (cada una con su propio Paging)
        for (int i = 1; i <= numPeticiones; i++) {
            
            // 1. GENERAR NOMBRE DE COLA ÚNICO
            // Se usa el timestamp y el contador para asegurar unicidad
            String nombreColaUnica = "TSQ" + System.currentTimeMillis() + i;
            
            // CICS TSQ names are often 8 characters maximum. 
            // We'll truncate the unique part to ensure it fits the mainframe limits.
            // Asumiendo un límite de 8 caracteres. Adaptar si se requiere más.
            String colaID = nombreColaUnica.substring(0, Math.min(nombreColaUnica.length(), 8)).toUpperCase(); 

            // 2. PREPARAR LA ENTRADA PARA LA PRIMERA LLAMADA
            // Se asume que el programa EGHLA001 lee el nombre de la cola
            // de las posiciones iniciales (ej. 8 caracteres) del COMMAREA.
            // El formato será: [COLA_ID] + [GC11NSS con padding]
            // Debes confirmar la posición exacta que espera tu programa CICS (EGHLA001).
            String primeraEntrada = colaID + "GC11NSS"; 
            
            // Aseguramos que la primeraEntrada tenga el formato esperado por EGHLA001.
            // Para simplificar, aquí se asume que tu programa CICS espera:
            // [Nombre de Cola de 8 caracteres] [GC11NSS, etc.]
            
            System.out.println("\n*** PETICIÓN #" + i + ": COLA GENERADA = " + colaID + " ***");

            // --- FASE 1: INICIAR LA CONSULTA Y ESCRIBIR DATOS EN LA COLA ---
            try {
                // La primera llamada al EGHLA001 envía el nombre de la cola única.
                // EGHLA001 ejecuta la consulta y escribe los resultados en la cola 'colaID'.
                System.out.println("  1. Enviando Setup (Programa: EGHLA001, Entrada: " + primeraEntrada + ")");
                test.servicioCICS.enviaReciveCadena(primeraEntrada, "USER", "PASS", "EGHLA001", "GC11");

            } catch (Exception e) {
                System.err.println("Error en Setup de Petición #" + i + ": " + e.getMessage());
                continue; // Salta a la siguiente petición si falla el setup
            }

            // --- FASE 2: RECUPERAR DATOS POR BLOQUES (PAGING) ---
            int bloqueCounter = 0;
            String ultimoBloque = null;
            
            do {
                // El primer parámetro ahora es el nombre de la cola única generado dinámicamente.
                // EGHLA002 lee el siguiente bloque de la cola 'colaID' y lo devuelve.
                ultimoBloque = test.servicioCICS.enviaReciveCadena(colaID, "USER", "PASS", "EGHLA002", "GC12");
                String respuestaLimpia = ultimoBloque.replaceAll(" ", "");
                
                System.out.println("    -> Bloque #" + ++bloqueCounter + ": " + respuestaLimpia);
                
                // Si la respuesta termina con "MAS", el bucle continúa para el siguiente bloque.
            } while (ultimoBloque != null && ultimoBloque.endsWith("MAS"));
            
            System.out.println("  2. Recuperación completa. Total de bloques: " + bloqueCounter);
            System.out.println("--------------------------------------------------");
        }
    }
}