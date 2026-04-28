package com.recon.api;

import com.recon.ingestion.config.KafkaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.recon")
@EnableJpaRepositories(basePackages = "com.recon")
@Import(KafkaConfig.class)
@EnableAsync
public class ReconApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconApiApplication.class, args);
    }
}
