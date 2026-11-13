package com.civicsphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CivicSphereApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivicSphereApplication.class, args);
    }
}

