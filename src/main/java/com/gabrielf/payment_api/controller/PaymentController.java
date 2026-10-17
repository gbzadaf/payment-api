package com.gabrielf.payment_api.controller;

import com.gabrielf.payment_api.dto.PaymentRequest;
import com.gabrielf.payment_api.dto.PaymentResponse;
import com.gabrielf.payment_api.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
@Tag(name = "Payments", description = "Criação, consulta e processamento de pagamentos")
@ApiResponse(responseCode = "401", description = "Token ausente, inválido ou expirado")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Cria um pagamento",
            description = "Cria um pagamento PENDING. Repetir a mesma Idempotency-Key com o mesmo "
                    + "corpo devolve o pagamento original; com corpo diferente, retorna 422.")
    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100)String idempotencyKey,
            @RequestBody @Valid PaymentRequest request) {

        PaymentResponse response = paymentService.create(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }

    @Operation(summary = "Busca um pagamento pelo id")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.findById(id));

    }

    @Operation(
            summary = "Processa um pagamento",
            description = "Envia a cobrança ao gateway. Valores acima de 5000,00 são recusados (FAILED). "
                    + "Dispara o webhook assinado. Pagamento que não está PENDING retorna 409.")
    @PostMapping("/{id}/process")
    public ResponseEntity<PaymentResponse> process(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.process(id));

    }
}
