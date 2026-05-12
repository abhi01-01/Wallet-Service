package com.wallet.walletservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wallettest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.pool-name=WalletServiceTestHikariPool",
        "spring.datasource.hikari.auto-commit=false",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class WalletServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
