package com.bulletonrails.rinha.service;

import com.bulletonrails.rinha.model.PaymentRequest;
import com.bulletonrails.rinha.model.PaymentSummary;
import com.bulletonrails.rinha.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentProcessorService processorService;
    private final HealthCheckService healthCheckService;

    @Autowired
    public PaymentService(PaymentRepository repository, 
                         PaymentProcessorService processorService,
                         HealthCheckService healthCheckService) {
        this.repository = repository;
        this.processorService = processorService;
        this.healthCheckService = healthCheckService;
    }

    public void receivePayment(PaymentRequest request) {
        Instant timestamp = Instant.now();
        HealthCheckService.ProcessorChoice choice = healthCheckService.getBestProcessor();
        
        // Record immediately like winning submission
        if (choice == HealthCheckService.ProcessorChoice.DEFAULT) {
            repository.recordDefaultPayment(request.correlationId(), request.amount(), timestamp);
        } else {
            repository.recordFallbackPayment(request.correlationId(), request.amount(), timestamp);
        }
        
        // Process synchronously for lower latency
        processorService.processPayment(request);
    }

    public PaymentSummary getSummary(Instant from, Instant to) {
        if (from == null || to == null) {
            return repository.getSummary();
        }
        return repository.getSummary(from, to);
    }

    public void purgePayments() {
        repository.purge();
    }
}
