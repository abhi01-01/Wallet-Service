package com.wallet.walletservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@Slf4j
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner loggingTest() {
        return args -> {
            System.out.println("====================================================");
            System.out.println("   WALLET SERVICE STARTUP SUCCESSFUL - SYSTEM.OUT   ");
            System.out.println("====================================================");
            log.info("WALLET SERVICE STARTUP SUCCESSFUL - SLF4J LOG");
        };
    }

}
