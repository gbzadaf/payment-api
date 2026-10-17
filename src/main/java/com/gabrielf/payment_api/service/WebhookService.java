package com.gabrielf.payment_api.service;

import com.gabrielf.payment_api.config.WebhookProperties;
import com.gabrielf.payment_api.dto.WebhookEvent;
import com.gabrielf.payment_api.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookProperties properties;
    private final WebhookSigner signer;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();


    @Async
    public void send(Payment payment) {
        String body = toJson(WebhookEvent.from(payment));
        String signature = signer.sign(body);

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                restClient.post()
                        .uri(properties.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", signature)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Webhook delivered for payment {} (attempt {})",  payment.getId(), attempt);
                return;
            } catch (Exception e) {
                log.warn("Webhook attempt {}/{} failed for payment {}: {}",
                        attempt, properties.maxAttempts(), payment.getId(), e.getMessage());
                if (attempt < properties.maxAttempts()) {
                    sleep();
                }
            }
        }
        log.error("Webhook gave up for payment {} after {} attempts",
                payment.getId(), properties.maxAttempts());
    }

    private String toJson(WebhookEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize webhook event", e);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(properties.retryDelayMs());
        }  catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
