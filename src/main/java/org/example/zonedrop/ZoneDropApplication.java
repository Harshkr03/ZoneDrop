package org.example.zonedrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZoneDropApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZoneDropApplication.class, args);
    }

}
