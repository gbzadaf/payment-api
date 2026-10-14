package com.gabrielf.payment_api.service;

import com.gabrielf.payment_api.config.WebhookProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebhookSignerTest {

    private static final String BODY = "{\"paymentId\":\"abc\",\"status\":\"APPROVED\"}";

    private WebhookSigner signerWith(String secret) {
        return new WebhookSigner(new WebhookProperties("http://localhost", secret, 3, 0));

    }

    @Test
    void sign_shouldReturnSameSignature_whenBodyAndSecretAreTheSame() {
        WebhookSigner signer = signerWith("secret");

        String first = signer.sign(BODY);
        String second = signer.sign(BODY);

        assertThat(first).isEqualTo(second);

    }

    @Test
    void sign_shouldReturnDifferentSignature_whenBodyChanges() {
        WebhookSigner signer = signerWith("secret");

        String original = signer.sign(BODY);
        String tampered = signer.sign(BODY.replace("APPROVED", "FAILED"));

        assertThat(tampered).isNotEqualTo(original);

    }

    @Test
    void sign_shouldReturnDifferentSignature_whenSecretChanges() {
        String withSecretA = signerWith("secret-a").sign(BODY);
        String withSecretB = signerWith("secret-b").sign(BODY);

        assertThat(withSecretB).isNotEqualTo(withSecretA);

    }

    @Test
    void sign_shouldReturn64LowercaseHexChars() {
        String signature = signerWith("secret").sign(BODY);

        assertThat(signature).matches("[0-9a-f]{64}");

    }
}
