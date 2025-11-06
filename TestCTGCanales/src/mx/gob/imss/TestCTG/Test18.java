package mx.gob.imss.TestCTG;

import java.io.Serializable;

import mx.gob.imss.TestCTG.services.ComunicacionCICSBean;
import mx.gob.imss.TestCTG.remote.ComunicacionCICSBeanRemote;

public class Test18 implements Serializable {

	private static final long serialVersionUID = -1528629493345218046L;
 
	
	ComunicacionCICSBeanRemote servicioCICS;
	
	public Test18()
	{
		servicioCICS = new ComunicacionCICSBean();
	}
	
	
	
	public static void main(String[] args) {
	
		Test18 test = new Test18();
		String respuesta = null;
		int counter = 0;

		System.out.println("enviaReciveCadena "  );
		test.servicioCICS.enviaReciveCadena("**********GC11NSS", "USER", "PASS","EGHLA001", "GC11");
		do
		{
			System.out.println("en el do enviaReciveCadena "  );
			respuesta = test.servicioCICS.enviaReciveCadena("QUEUE", "USER", "PASS","EGHLA002", "GC12").replaceAll(" ", "");
			System.out.println("Registro:" + respuesta);
			System.out.println("Counter: " + counter);
			counter++;
		}while(respuesta.endsWith("MAS"));
		
		
	}
	
 
}