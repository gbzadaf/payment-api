package com.gabrielf.payment_api.controller;

import com.gabrielf.payment_api.dto.LoginRequest;
import com.gabrielf.payment_api.dto.LoginResponse;
import com.gabrielf.payment_api.dto.RegisterRequest;
import com.gabrielf.payment_api.security.JwtService;
import com.gabrielf.payment_api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Cadastro e login")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserService userService;

    @Operation(summary = "Cadastra um usuário",
            description = "E-mail único (normalizado em minúsculas) e senha de 8 a 72 caracteres.")
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Autentica e devolve um token JWT",
            description = "Use o token no botão Authorize para acessar os endpoints de pagamento.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(LoginResponse.of(jwtService.generateToken(userDetails)));

    }
}
