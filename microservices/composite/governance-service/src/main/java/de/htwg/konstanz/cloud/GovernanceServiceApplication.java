package de.htwg.konstanz.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
// To auto register microservice with Eureka
//@EnableDiscoveryClient
public class GovernanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GovernanceServiceApplication.class, args);
    }
}