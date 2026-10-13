package com.gabrielf.payment_api.service;

import com.gabrielf.payment_api.dto.PaymentRequest;
import com.gabrielf.payment_api.dto.PaymentResponse;
import com.gabrielf.payment_api.exception.IdempotencyKeyReuseException;
import com.gabrielf.payment_api.exception.InvalidPaymentStateException;
import com.gabrielf.payment_api.exception.PaymentNotFoundException;
import com.gabrielf.payment_api.gateway.GatewayResult;
import com.gabrielf.payment_api.gateway.PaymentGateway;
import com.gabrielf.payment_api.model.Payment;
import com.gabrielf.payment_api.model.enums.PaymentStatus;
import com.gabrielf.payment_api.repository.PaymentRepository;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    private static final String KEY = "key-123";

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PaymentGateway paymentGateway;

    private PaymentRequest request(String amount) {
        return new PaymentRequest(new BigDecimal(amount), "BRL", "Pedido #1");
    }

    private Payment existingPayment(String amount) {
        return new Payment(new BigDecimal(amount), "BRL", "Pedido #1", KEY);
    }

    @Test
    void create_shouldSaveAndReturnPayment_whenKeyIsNew() {
        when(paymentRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.create(KEY, request("100.00"));

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.amount()).isEqualByComparingTo("100.00");
        verify(paymentRepository).saveAndFlush(any(Payment.class));

    }

    @Test
    void create_shouldReturnExistingPayment_whenKeyIsReusedWithSamePayload() {
        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existingPayment("100.00")));

        PaymentResponse response = paymentService.create(KEY, request("100.0"));

        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).saveAndFlush(any());


    }

    @Test
    void create_shouldThrow_whenKeyIsReusedWithDifferentAmount() {
        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existingPayment("100.00")));

        assertThatThrownBy(() -> paymentService.create(KEY, request("200.00")))
                .isInstanceOf(IdempotencyKeyReuseException.class);

        verify(paymentRepository, never()).saveAndFlush(any());

    }

    @Test
    void create_shouldReturnWinnerPayment_whenConcurrentInsertViolatesUniqueKey() {
        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty(), Optional.of(existingPayment("100.00")));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        PaymentResponse response = paymentService.create(KEY, request("100.00"));

        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, times(2)).findByIdempotencyKey(KEY);

    }

    @Test
    void findById_shouldReturnPayment_whenIdExists() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id))
                .thenReturn(Optional.of(existingPayment("100.00")));

        PaymentResponse response = paymentService.findById(id);

        assertThat(response.amount()).isEqualByComparingTo("100.00");
        assertThat(response.currency()).isEqualTo("BRL");
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);

    }

    @Test
    void findById_shouldThrowNotFound_whenIdDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());

    }

    //Esse teste protege contra um erro silencioso: se alguém alterar o catch pra devolver algo em vez de
    // relançar, um problema real de integridade passaria despercebido.
    @Test
    void create_shouldRethrowViolation_whenInsertFailsAndNoPaymentWithKeyExists() {
        when(paymentRepository.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("other constraint"));

        assertThatThrownBy(() -> paymentService.create(KEY, request("100.00")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("other constraint");

        verify(paymentRepository, times(2)).findByIdempotencyKey(KEY);

    }

    @Test
    void process_shouldApprove_whenGatewayApproves() {
        UUID id = UUID.randomUUID();
        Payment pending = existingPayment("100.00");

        when(paymentRepository.findById(id)).thenReturn(Optional.of(pending));
        when(paymentGateway.charge(pending)).thenReturn(GatewayResult.APPROVED);
        when(paymentRepository.saveAndFlush(pending)).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.process(id);

        assertThat(response.status()).isEqualTo(PaymentStatus.APPROVED);
        verify(paymentGateway).charge(pending);

    }

    @Test
    void process_shouldThrowInvalidState_whenPaymentAlreadyApproved() {
        UUID id = UUID.randomUUID();
        Payment approved = existingPayment("100.00");
        approved.approve();
        when(paymentRepository.findById(id)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> paymentService.process(id))
                .isInstanceOf(InvalidPaymentStateException.class);

        verify(paymentGateway, never()).charge(any());
        verify(paymentRepository, never()).saveAndFlush(any());

    }

    @Test
    void process_shouldDecline_whenGatewayDecline() {
        UUID id = UUID.randomUUID();
        Payment pending = existingPayment("100.00");

        when(paymentRepository.findById(id)).thenReturn(Optional.of(pending));
        when(paymentGateway.charge(pending)).thenReturn(GatewayResult.DECLINED);
        when(paymentRepository.saveAndFlush(pending)).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.process(id);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentGateway).charge(pending);
        verify(paymentRepository).saveAndFlush(pending);

    }

    @Test
    void process_shouldThrowNotFound_whenIdDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(()-> paymentService.process(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(paymentGateway, never()).charge(any());
        verify(paymentRepository, never()).saveAndFlush(any());

    }

}
