package com.wallet.walletservice.service.payment.cleanup;

import com.wallet.walletservice.repository.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCleanupService {

    private final PaymentOrderRepository paymentOrderRepository;

    public void cleanUpStaleOrders() {
        OffsetDateTime currentTime = OffsetDateTime.now();
        OffsetDateTime cutOff = currentTime.minusHours(2);
        int updatedCount = paymentOrderRepository.failStaleOrders(cutOff, currentTime);
        log.info("Swept and failed {} stale payment orders.", updatedCount);
    }
}
