package com.recon.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.recon")
@EnableAsync
public class ReconApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconApiApplication.class, args);
    }
}

