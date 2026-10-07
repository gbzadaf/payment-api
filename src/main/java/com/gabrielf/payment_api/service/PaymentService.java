package com.gabrielf.payment_api.service;

import com.gabrielf.payment_api.dto.PaymentRequest;
import com.gabrielf.payment_api.dto.PaymentResponse;
import com.gabrielf.payment_api.exception.IdempotencyKeyReuseException;
import com.gabrielf.payment_api.exception.PaymentNotFoundException;
import com.gabrielf.payment_api.model.Payment;
import com.gabrielf.payment_api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /*sem transactional, se o create fosse transactional, o findByIdempotencyKey dentro do catch quebraria. Sem a anotação,
    cada chamada ao repository roda na própria transação: o saveAndFlush falha e fecha a dele, e a busca seguinte abre
    uma nova.
     */
    public PaymentResponse create(String idempotencyKey, PaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, idempotencyKey, request))
                .orElseGet(() -> insert(idempotencyKey, request));

    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(id));

    }


    private PaymentResponse insert(String idempotencyKey, PaymentRequest request) {
        Payment payment = new Payment(
                request.amount(),
                request.currency(),
                request.description(),
                idempotencyKey
        );
        try {                         //saveandflush pra capturar violacao na hora
            return PaymentResponse.from(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException e) {
            // Outra requisição com a mesma chave venceu a corrida
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> replay(existing, idempotencyKey, request))
                    .orElseThrow(() -> e);
        }
    }

    private PaymentResponse replay(Payment existing, String idempotencyKey, PaymentRequest request) {
        //compareTo ao inves de equals POR CAUSA do BigDecimal.
        boolean sameRequest = existing.getAmount().compareTo(request.amount()) == 0
        && existing.getCurrency().equals(request.currency());
        if (!sameRequest) {
            throw new IdempotencyKeyReuseException(idempotencyKey);
        }
        return PaymentResponse.from(existing);
    }
}
