package com.wallet.walletservice.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.entity.WebhookEvent;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.repository.UserRepository;
import com.wallet.walletservice.service.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:paymentcapturedstrategytest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
public class PaymentCapturedStrategyIntegrationTest {

    @Autowired
    private PaymentCapturedStrategy paymentCapturedStrategy;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean // Mock the WalletService so we can verify interactions without triggering actual ledger logic
    private WalletService walletService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void givenClosedUser_whenWebhookArrives_thenThrowExceptionAndPreventCredit() throws Exception{

        // 1. Set up a structurally valid CLOSED user
        User closedUser = new User();
        closedUser.setEmail("closed.user_" + System.currentTimeMillis() + "@test.com"); // Mandatory & Unique
        closedUser.setProvider(AuthProvider.EMAIL); // Mandatory
        closedUser.setAccountStatus(UserStatus.CLOSED);
        // Use saveAndFlush to instantly catch any constraint violations during setup, rather than later
        closedUser = userRepository.saveAndFlush(closedUser);
        String userId = closedUser.getId().toString();

        // 2. Set up a CREATED payment order for the closed user
        PaymentOrder order = new PaymentOrder();
        order.setUserId(userId);
        order.setRazorpayOrderId("order_test_123");
        order.setStatus(PaymentOrderStatus.CREATED);
        order.setAmount(new BigDecimal("100.00"));
        order.setAssetCode("GOLD");
        paymentOrderRepository.saveAndFlush(order);

        // 3. Construct the Webhook JSON payload
        String jsonPayload = """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "pay_test_999",
                        "order_id": "order_test_123"
                      }
                    }
                  }
                }
                """;

        WebhookEvent event = new WebhookEvent();
        event.setEventType("payment.captured");
        event.setPayload(objectMapper.readTree(jsonPayload));

        // 4. Execute and Assert
        // The strategy should throw an AuthException because the user guard blocks it
        assertThrows(AuthException.class, () -> paymentCapturedStrategy.process(event));

        // 5. Verify the state was NOT corrupted
        verify(walletService, never()).topUp(any()); // The wallet was never credited

        PaymentOrder unchangedOrder = paymentOrderRepository.findByRazorpayOrderId("order_test_123").orElseThrow();
        assertEquals(PaymentOrderStatus.CREATED, unchangedOrder.getStatus());
    }
}
