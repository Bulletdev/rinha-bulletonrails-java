package com.bulletonrails.rinha.service;

import com.bulletonrails.rinha.model.HealthStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class HealthCheckService {

    private final WebClient webClient;
    private final AtomicReference<HealthStatus> defaultHealth = new AtomicReference<>();
    private final AtomicReference<HealthStatus> fallbackHealth = new AtomicReference<>();
    private final AtomicReference<Instant> lastCheck = new AtomicReference<>(Instant.EPOCH);

    @Autowired
    public HealthCheckService(WebClient webClient) {
        this.webClient = webClient;
        defaultHealth.set(new HealthStatus(false, 50));
        fallbackHealth.set(new HealthStatus(false, 100));
    }

    @Scheduled(fixedRate = 10000)
    public void checkHealth() {
        lastCheck.set(Instant.now());

        checkProcessorHealth("http://payment-processor-default:8080/payments/service-health")
            .subscribe(defaultHealth::set,
                      error -> defaultHealth.set(new HealthStatus(true, 1000)));

        checkProcessorHealth("http://payment-processor-fallback:8080/payments/service-health")
            .subscribe(fallbackHealth::set,
                      error -> fallbackHealth.set(new HealthStatus(true, 1000)));
    }

    private Mono<HealthStatus> checkProcessorHealth(String url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(HealthStatus.class)
            .timeout(Duration.ofMillis(300))
            .onErrorReturn(new HealthStatus(true, 1000));
    }

    public ProcessorChoice getBestProcessor() {
        HealthStatus defaultStat = defaultHealth.get();
        HealthStatus fallbackStat = fallbackHealth.get();

        if (defaultStat.failing() && fallbackStat.failing()) {
            return ProcessorChoice.DEFAULT;
        }

        if (defaultStat.isHealthy() && !fallbackStat.isHealthy()) {
            return ProcessorChoice.DEFAULT;
        }
        if (!defaultStat.isHealthy() && fallbackStat.isHealthy()) {
            return ProcessorChoice.FALLBACK;
        }

        return defaultStat.getScore() <= fallbackStat.getScore() * 1.5
            ? ProcessorChoice.DEFAULT 
            : ProcessorChoice.FALLBACK;
    }

    public enum ProcessorChoice {
        DEFAULT("http://payment-processor-default:8080/payments"),
        FALLBACK("http://payment-processor-fallback:8080/payments");

        public final String url;

        ProcessorChoice(String url) {
            this.url = url;
        }
    }
}
