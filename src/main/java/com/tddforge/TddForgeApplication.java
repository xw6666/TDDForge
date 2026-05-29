package com.tddforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TddForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TddForgeApplication.class, args);
    }
}
