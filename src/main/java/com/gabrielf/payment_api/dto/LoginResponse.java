package com.gabrielf.payment_api.dto;

public record LoginResponse(
        String token,
        String tokenType

) {
    public static LoginResponse of(String token) {
        return new LoginResponse(token, "Bearer");

    }
}
