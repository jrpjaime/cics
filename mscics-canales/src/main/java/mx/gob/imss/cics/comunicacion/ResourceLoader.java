package mx.gob.imss.cics.comunicacion;

import java.io.InputStream;


public class ResourceLoader {

	protected ResourceLoader() {
		super();
	}

	public static InputStream load( String resourceName ) {

		InputStream resource = null;

		resource = ResourceLoader.class.getClassLoader().getResourceAsStream( resourceName );
		if( resource == null ) {

			ClassLoader classLoader = ResourceLoader.class.getClassLoader().getParent();
			if( classLoader != null ) resource = classLoader.getResourceAsStream( resourceName );
			if( resource == null ) {
				classLoader = Thread.currentThread().getClass().getClassLoader();
				if( classLoader != null ) resource = classLoader.getResourceAsStream( resourceName );
			}
		}

		return resource;
	}
}
