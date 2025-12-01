# Usar una imagen base con Java 17
FROM openjdk:17-slim

# Directorio donde se colocará la aplicación en el contenedor
WORKDIR /app

# Copiar el archivo jar del proyecto al directorio /app en el contenedor
COPY target/mscics-canales-0.0.1-SNAPSHOT.jar /app/mscics-canales.jar

# Exponer el puerto que usa la aplicación
EXPOSE 4510

# Comando para ejecutar la aplicación
CMD ["java", "-jar", "/app/mscics-canales.jar"]
