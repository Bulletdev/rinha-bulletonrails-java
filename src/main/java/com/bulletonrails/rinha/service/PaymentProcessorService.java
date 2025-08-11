package com.bulletonrails.rinha.service;

import com.bulletonrails.rinha.model.PaymentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
public class PaymentProcessorService {

    private final WebClient webClient;
    private final HealthCheckService healthCheckService;

    @Autowired
    public PaymentProcessorService(WebClient webClient, HealthCheckService healthCheckService) {
        this.webClient = webClient;
        this.healthCheckService = healthCheckService;
    }

    public CompletableFuture<Boolean> processPayment(PaymentRequest request) {
        return processPaymentAsync(request).toFuture();
    }

    private Mono<Boolean> processPaymentAsync(PaymentRequest request) {
        HealthCheckService.ProcessorChoice choice = healthCheckService.getBestProcessor();

        return callProcessor(choice.url, request)
            .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(50)))
            .onErrorResume(error -> {
                HealthCheckService.ProcessorChoice fallback = choice == HealthCheckService.ProcessorChoice.DEFAULT
                    ? HealthCheckService.ProcessorChoice.FALLBACK 
                    : HealthCheckService.ProcessorChoice.DEFAULT;

                return callProcessor(fallback.url, request)
                    .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(25)));
            })
            .onErrorReturn(false);
    }

    private Mono<Boolean> callProcessor(String url, PaymentRequest request) {
        return webClient.post()
            .uri(url)
            .bodyValue(request.toProcessorRequest())
            .retrieve()
            .bodyToMono(String.class)
            .map(response -> true)
            .timeout(Duration.ofMillis(600));
    }

    public HealthCheckService.ProcessorChoice getLastUsedProcessor(PaymentRequest request) {
        return healthCheckService.getBestProcessor();
    }
}
