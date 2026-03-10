# Documentação da Arquitetura - Rinha BulletOnRails Java

## Visão Geral

Sistema de processamento de pagamentos de alta performance para a
Rinha de Backend, usando Java 21 + Spring Boot 3.3, Redis para estado
compartilhado e Nginx como load balancer.

## Arquitetura Geral

### Camadas da Aplicação

1. **Camada de Apresentação (Controller Layer)**
   - `PaymentController`: Endpoints REST
     - `POST /payments` — recebe e roteia pagamentos
     - `GET /payments-summary` — resumo por processador (com filtro de data)
     - `POST /purge-payments` — limpa dados

2. **Camada de Serviço (Service Layer)**
   - `PaymentService`: registra otimisticamente no Redis e dispara
     chamada assíncrona ao processador (fire-and-forget)
   - `HealthCheckService`: monitora saúde dos processadores a cada 5s
     e implementa routing inteligente por score
   - `PaymentProcessorService`: HTTP reativo com retry e fallback entre
     processadores

3. **Camada de Dados (Repository Layer)**
   - `PaymentRepository`: Redis via `StringRedisTemplate` com pipelining
     nas escritas para menor latência
   - Contadores atômicos: `INCR` / `INCRBY` no Redis
   - Registros individuais: sorted set `ZADD` com score = epoch ms

4. **Camada de Configuração**
   - `HttpClientConfig`: WebClient com pool de 200 conexões, keep-alive
   - `AsyncConfig`: thread pools para processamento assíncrono

## Componentes Detalhados

### PaymentController
- Expõe endpoints REST
- Filtro de data (`from`/`to`) repassado ao serviço como `Instant`

### PaymentService
- **Optimistic recording**: pagamento registrado no Redis antes da
  chamada ao processador → resposta rápida
- **Fire-and-forget**: `processorService.processPayment()` executado
  de forma assíncrona, sem bloquear o cliente
- Routing via `HealthCheckService.getBestProcessor()`

### HealthCheckService
- Health checks a cada **5 segundos** (reduzido de 10s)
- Score baseado em `minResponseTime` retornado pelo processador
- Prefere DEFAULT quando seu score ≤ 1.5× o do FALLBACK
- Fallback automático se DEFAULT falhar

### PaymentProcessorService
- Retry com delay fixo (1 retry, 50ms)
- Fallback automático para o outro processador em caso de erro
- Timeout de 600ms por chamada

### PaymentRepository — Redis
| Operação | Comando Redis |
|---|---|
| Incrementar contador | `INCR pay:d:req` |
| Somar valor | `INCRBY pay:d:amt <cents>` |
| Gravar registro | `ZADD pay:rec <epoch_ms> "<uuid>\|<cents>\|<1/0>"` |
| Summary geral | 4× `GET` em pipeline |
| Summary filtrado | `ZRANGEBYSCORE` + agregação client-side |
| Purge | `DEL` das 5 chaves |

Escritas usam `executePipelined` — 3 comandos em 1 round-trip.

## Arquitetura de Deploy

### Containers Docker

| Container | CPU | RAM | Função |
|---|---|---|---|
| Redis 7 Alpine | 0.1 | 30MB | Estado compartilhado |
| backend-1 | 0.6 | 150MB | Instância Spring Boot |
| backend-2 | 0.6 | 150MB | Instância Spring Boot |
| load-balancer | 0.2 | 20MB | Nginx least_conn |
| **Total** | **1.5** | **350MB** | |

### Redes Docker

- **backend**: comunicação interna LB ↔ Backends ↔ Redis
- **payment-processor**: rede externa para os processadores

## Fluxo de Pagamento

```
Cliente → Nginx (least_conn)
       → Backend (qualquer instância)
         → HealthCheckService.getBestProcessor()    ← cache em memória
         → PaymentRepository.record*Payment()       → Redis (pipeline)
         → PaymentProcessorService.processPayment() → Processador externo (async)
         → 200 OK
```

## Fluxo de Summary

```
Cliente → Nginx → Backend
       → PaymentRepository.getSummary(from, to)
         → Redis ZRANGEBYSCORE (filtro) ou 4× GET (total)
       → JSON response
```

## Otimizações de Performance

### Escrita (hot path)
- Registro no Redis em pipeline: 1 round-trip para 3 comandos
- Chamada ao processador assíncrona: não bloqueia resposta
- Virtual Threads (Java 21): I/O eficiente sem overhead de threads OS

### Leitura (summary)
- Summary total: 4 GETs em pipeline — sem iteração
- Summary filtrado: ZRANGEBYSCORE + loop no cliente

### JVM
- G1GC com pausa máxima de 5ms
- Heap fixo em 100MB (`-Xms100m -Xmx100m`)
- TieredCompilation nível 1 para startup rápido (~3s)
- `-XX:+AlwaysPreTouch` para pré-alocar heap

### HTTP / Rede
- Undertow (substitui Tomcat)
- Connection pool: 200 conexões, keep-alive
- Nginx: least_conn, keepalive 256, epoll

## Decisões de Arquitetura

### Por que Redis para estado compartilhado?
Com 2 instâncias backend em memória isolada, o `GET /payments-summary`
retornaria valores inconsistentes dependendo de qual instância
respondesse. Redis resolve isso com operações atômicas sem necessidade
de sincronização entre instâncias.

### Por que optimistic recording + fire-and-forget?
Gravar primeiro e chamar o processador depois garante que o P99 não
seja afetado pela latência do processador externo. A chamada assíncrona
ainda chega ao processador, mas o cliente recebe 200 OK imediatamente.

### Por que sorted set para registros individuais?
`ZADD` com score = timestamp permite queries eficientes de range
(`ZRANGEBYSCORE`) sem varrer todos os dados. Complexidade O(log N + M)
onde M é o número de resultados no intervalo.
