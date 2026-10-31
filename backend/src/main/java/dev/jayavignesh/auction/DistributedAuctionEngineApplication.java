package dev.jayavignesh.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DistributedAuctionEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                DistributedAuctionEngineApplication.class,
                args
        );
    }
}
