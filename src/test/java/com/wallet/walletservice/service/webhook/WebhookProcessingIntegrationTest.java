package com.wallet.walletservice.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.walletservice.domain.entity.AssetType;
import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.entity.WebhookEvent;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.domain.enums.WebhookStatus;
import com.wallet.walletservice.repository.AssetTypeRepository;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.repository.TransactionRepository;
import com.wallet.walletservice.repository.UserRepository;
import com.wallet.walletservice.repository.WalletRepository;
import com.wallet.walletservice.repository.WebhookEventRepository;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webhookprocessingtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class WebhookProcessingIntegrationTest {

    @Autowired
    private WebhookPollerJob webhookPollerJob;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AssetTypeRepository assetTypeRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private PaymentOrderRepository paymentOrderRepository;
    @Autowired
    private WebhookEventRepository webhookEventRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void pollAndProcess_WhenPaymentCapturedEventExists_MarksOrderPaidAndCreditsWalletOnce() throws Exception {
        User user = userRepository.save(User.builder()
                .email("webhook-credit@example.com")
                .provider(AuthProvider.EMAIL)
                .ownerType(OwnerType.USER)
                .emailVerified(true)
                .build());

        AssetType gold = assetTypeRepository.save(AssetType.builder()
                .name("Gold Coins")
                .code("GOLD")
                .description("Test asset")
                .build());

        walletRepository.save(Wallet.builder()
                .ownerId(WalletProvider.SYSTEM_TREASURY)
                .ownerType(OwnerType.SYSTEM)
                .assetType(gold)
                .balance(new BigDecimal("1000.0000"))
                .build());

        paymentOrderRepository.save(PaymentOrder.builder()
                .userId(user.getId().toString())
                .razorpayOrderId("order_webhook_process_1")
                .amount(new BigDecimal("125.0000"))
                .assetCode("GOLD")
                .status(PaymentOrderStatus.CREATED)
                .build());

        String payload = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_webhook_process_1","order_id":"order_webhook_process_1"}}}}
                """.trim();
        webhookEventRepository.save(WebhookEvent.builder()
                .eventId("pay_webhook_process_1")
                .eventType("payment.captured")
                .orderId("order_webhook_process_1")
                .payload(objectMapper.readTree(payload))
                .build());

        webhookPollerJob.pollAndProcess();

        WebhookEvent event = webhookEventRepository.findByEventId("pay_webhook_process_1").orElseThrow();
        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderId("order_webhook_process_1").orElseThrow();
        Wallet userWallet = walletRepository.findByOwnerIdAndAssetCode(user.getId().toString(), "GOLD").orElseThrow();
        Wallet treasuryWallet = walletRepository.findByOwnerIdAndAssetCode(WalletProvider.SYSTEM_TREASURY, "GOLD").orElseThrow();

        assertEquals(WebhookStatus.PROCESSED, event.getStatus());
        assertEquals(1, event.getProcessingAttempts());
        assertEquals(PaymentOrderStatus.PAID, order.getStatus());
        assertEquals("pay_webhook_process_1", order.getRazorpayPaymentId());
        assertEquals(0, userWallet.getBalance().compareTo(new BigDecimal("125.0000")));
        assertEquals(0, treasuryWallet.getBalance().compareTo(new BigDecimal("875.0000")));
        assertEquals(1, transactionRepository.findAll().stream()
                .filter(txn -> txn.getTransactionType() == TransactionType.TOPUP)
                .count());
        assertEquals(2, ledgerEntryRepository.count());
    }
}
