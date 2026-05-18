package com.wallet.walletservice.service.payment.cleanup;

import com.wallet.walletservice.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSweeperJob {

    private final PaymentService paymentService;

    // Runs every 2 hours. ShedLock prevents multiple app instances from sweeping together.
    @Scheduled(cron = "0 0 0/2 * * *")
    @SchedulerLock(name = "stale_orders_cleanup_lock", lockAtLeastFor = "2m", lockAtMostFor = "5m")
    public void cleanupStalePaymentOrders() {
        log.info("Acquired distributed lock. Executing stale order cleanup...");
        paymentService.cleanUpStaleOrders();
    }
}
