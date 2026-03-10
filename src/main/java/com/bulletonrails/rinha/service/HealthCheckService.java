package com.bulletonrails.rinha.service;

import com.bulletonrails.rinha.model.HealthStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class HealthCheckService {

    private final WebClient webClient;

    private final AtomicReference<HealthStatus> defaultHealth =
        new AtomicReference<>(new HealthStatus(false, 50));

    private final AtomicReference<HealthStatus> fallbackHealth =
        new AtomicReference<>(new HealthStatus(true, 1000));

    @Autowired
    public HealthCheckService(WebClient webClient) {
        this.webClient = webClient;
    }

    @Scheduled(fixedRate = 5000)
    public void checkHealth() {
        checkProcessorHealth(
                "http://payment-processor-default:8080"
                + "/payments/service-health")
            .subscribe(defaultHealth::set,
                error -> defaultHealth.set(new HealthStatus(true, 1000)));

        checkProcessorHealth(
                "http://payment-processor-fallback:8080"
                + "/payments/service-health")
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

        // Prefere o default se não for mais que 1.5x mais lento
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
