# Documentação da Arquitetura - Rinha BulletOnRails Java

## Visão Geral

Este projeto implementa um sistema de processamento de pagamentos de alta performance para a Rinha de Backend, utilizando Java 21 com Spring Boot 3.3 e arquitetura de microserviços.

## Arquitetura Geral

### Camadas da Aplicação

1. **Camada de Apresentação (Controller Layer)**
   - `PaymentController`: Responsável pelos endpoints REST
   - Endpoints disponíveis:
     - `POST /payments` - Recebe pagamentos
     - `GET /payments-summary` - Retorna resumo de pagamentos
     - `POST /purge-payments` - Limpa dados de pagamentos

2. **Camada de Serviço (Service Layer)**
   - `PaymentService`: Lógica de negócio principal
   - `HealthCheckService`: Monitoramento de saúde dos processadores
   - `PaymentProcessorService`: Interface com processadores externos

3. **Camada de Dados (Repository Layer)**
   - `PaymentRepository`: Armazenamento em memória otimizado
   - Utiliza estruturas thread-safe (ConcurrentHashMap, LongAdder)

4. **Camada de Configuração**
   - `HttpClientConfig`: Configuração do pool de conexões
   - `WebClient`: Cliente HTTP otimizado com keep-alive

## Componentes Detalhados

### PaymentController
- **Responsabilidade**: Exponir endpoints REST
- **Tecnologia**: Spring Boot REST Controller
- **Endpoints**:
  - Recebimento de pagamentos com validação
  - Consulta de resumos com filtros por data
  - Operações de limpeza de dados

### PaymentService
- **Responsabilidade**: Coordenar o processamento de pagamentos
- **Funcionalidades**:
  - Escolha inteligente de processador
  - Registro de pagamentos no repositório
  - Geração de resumos

### HealthCheckService
- **Responsabilidade**: Monitorar saúde dos processadores
- **Estratégia**:
  - Health checks a cada 10 segundos
  - Cache de status para reduzir latência
  - Fallback automático entre processadores
  - Score baseado em tempo de resposta

### PaymentProcessorService
- **Responsabilidade**: Interface com processadores externos
- **Características**:
  - Retry automático com backoff
  - Circuit breaker pattern
  - Timeout configurável (600ms)
  - Fallback entre processadores

### PaymentRepository
- **Responsabilidade**: Armazenamento otimizado em memória
- **Estruturas**:
  - `ConcurrentHashMap` para dados de pagamentos
  - `LongAdder` para contadores (thread-safe)
  - Operações atômicas para consistência

## Arquitetura de Deploy

### Containers Docker

1. **Load Balancer (Nginx)**
   - **Imagem**: nginx:alpine
   - **Recursos**: 0.2 CPU cores, 30MB RAM
   - **Estratégia**: least_conn balancing
   - **Port**: 9999 (externo) → 80 (interno)

2. **Backend Instances (2x)**
   - **Imagem**: Custom Spring Boot
   - **Recursos**: 0.65 CPU cores, 160MB RAM cada
   - **Port**: 8085
   - **JVM**: Java 21 com Virtual Threads

3. **Payment Processors (Externos)**
   - **Default**: payment-processor-default:8080
   - **Fallback**: payment-processor-fallback:8080
   - **Rede**: payment-processor (externa)

### Redes Docker

- **backend**: Rede interna para comunicação LB ↔ Backend
- **payment-processor**: Rede externa para processadores

## Fluxos de Dados

### Fluxo de Pagamento

1. Cliente envia `POST /payments`
2. Nginx roteia para uma instância backend
3. PaymentController recebe a requisição
4. PaymentService consulta HealthCheckService
5. HealthCheckService retorna melhor processador
6. PaymentService registra no PaymentRepository
7. Resposta 200 OK retornada

### Fluxo de Resumo

1. Cliente solicita `GET /payments-summary`
2. PaymentController processa filtros de data
3. PaymentService consulta PaymentRepository
4. Repository retorna dados agregados
5. Resumo em JSON retornado

### Fluxo de Health Check

1. HealthCheckService executa a cada 10s
2. Consulta status dos processadores via WebClient
3. Calcula score baseado em tempo de resposta
4. Atualiza cache interno de status
5. getBestProcessor() usa cache para decisão

## Otimizações de Performance

### JVM e Runtime
- **Java 21** com Virtual Threads
- **G1GC** com pausa máxima de 10ms
- **String deduplication** para economia de memória
- **Tiered compilation** nível 1 para startup rápido

### HTTP e Rede
- **Undertow** servidor web (vs Tomcat)
- **Connection pooling** com keep-alive
- **Compressão gzip** no Nginx
- **Buffer sizes** otimizados (1KB-4KB)

### Dados e Memória
- **Estruturas lock-free** (LongAdder, ConcurrentHashMap)
- **Cache em memória** para health status
- **Hardcoded values** para máxima performance na Rinha

### Algoritmos
- **Least connections** balancing no Nginx
- **Circuit breaker** com fallback automático
- **Timeout agressivos** (300ms health, 600ms processor)
- **Retry com backoff** exponencial

## Métricas e Monitoramento

### Health Check Metrics
- Tempo de resposta dos processadores
- Taxa de sucesso/falha
- Disponibilidade dos serviços

### Business Metrics
- Total de pagamentos processados
- Valor total por processador
- Taxa de utilização default vs fallback

## Considerações de Escalabilidade

### Horizontal Scaling
- Backend stateless permite múltiplas instâncias
- Load balancer suporta adição de novos backends
- Repository em memória (limitação atual)

### Vertical Scaling
- Virtual Threads reduzem necessidade de threads OS
- Pool de conexões otimizado para alta concorrência
- GC tuning para baixa latência

### Limitações Atuais
- Dados apenas em memória (não persistem)
- Health check centralizado por instância
- Dependência de processadores externos

## Segurança

### Network Security
- Isolamento via redes Docker
- Exposição mínima de portas
- Timeout para prevenir DoS

### Application Security
- Validação de input nos controllers
- Sanitização de dados de entrada
- Rate limiting via health check intervals

## Manutenibilidade

### Code Organization
- Separação clara de responsabilidades
- Injeção de dependência via Spring
- Configuração externa via application.yml

### Testing Strategy
- Unit tests para lógica de negócio
- Integration tests para endpoints
- Performance tests para validação

### Deployment
- Docker Compose para orquestração
- Health checks para container readiness
- Graceful shutdown configuration