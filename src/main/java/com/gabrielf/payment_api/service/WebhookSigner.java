package com.gabrielf.payment_api.service;

import com.gabrielf.payment_api.config.WebhookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;


@Component
@RequiredArgsConstructor
public class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final WebhookProperties properties;


    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.secret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign webhook payload", e);
        }
    }
}
