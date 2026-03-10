package com.bulletonrails.rinha.controller;

import com.bulletonrails.rinha.model.PaymentRequest;
import com.bulletonrails.rinha.model.PaymentSummary;
import com.bulletonrails.rinha.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ResponseEntity<Void> receivePayment(
            @RequestBody PaymentRequest request) {
        paymentService.receivePayment(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/payments-summary")
    public ResponseEntity<PaymentSummary> getPaymentsSummary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        Instant fromInstant = from != null ? Instant.parse(from) : null;
        Instant toInstant = to != null ? Instant.parse(to) : null;

        PaymentSummary summary =
            paymentService.getSummary(fromInstant, toInstant);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/purge-payments")
    public ResponseEntity<Void> purgePayments() {
        paymentService.purgePayments();
        return ResponseEntity.ok().build();
    }
}
