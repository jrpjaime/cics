package mx.gob.imss.cics;

import java.util.TimeZone;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class mssicimssConsultasApplication {

    public static void main(String[] args) {
        SpringApplication.run(mssicimssConsultasApplication.class, args);
    }


    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"));
    }

       @Bean
    public CommandLineRunner generateHash() {
        return args -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            String rawPassword = "J@1m3_2025";
            String encodedPassword = encoder.encode(rawPassword);
            System.out.println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::");
            System.out.println("HASH GENERADO PARA " + rawPassword + ": " + encodedPassword);
            System.out.println(":::::::::::::::::::::::::::::::::::::::::::::::::::::::::::");
        };
    }

}
