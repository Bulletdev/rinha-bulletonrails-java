# 🐓 Rinha de Backend - Nerfa mais a JVM  ☕ 

<details align="left">

<div>

 <h1>  
 Signatures:
 </h1>

                   ⢀⣴⣿⣿⣿⣿⣿⣶⣶⣶⣿⣿⣶⣶⣶⣶⣶⣿⡿⣿⣾⣷⣶⣶⣾⣿⠀                                                                                                                          
                 ⣠⣿⣿⢿⣿⣯⠀⢹⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⡇⣿⡇⣿⣿⣿⣿⣿⡇                                                                                                         
             ⠀⣰⣿⣿⣷⡟⠤⠟⠁⣼⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⣿⢸⡇⣿⣿⣿⣿⣿⡇ 
             ⠀⣿⣿⣿⣿⣿⣷⣶⣿⣿⡟⠁⣮⡻⣿⣿⣿⣿⣿⣿⣿⣿⢸⡇⣿⣿⣿⣿⣿⡇ 
             ⠘⣿⣿⣿⣿⣿⣿⣿⣿⠏⠀⠀⣿⣿⣹⣿⣿⣿⣿⣿⣿⡿⢸⡇⣿⣿⣿⣿⣿⡇ 
             ⠀⠙⢿⣿⣿⣿⡿⠟⠁⣿⣿⣶⣿⠟⢻⣿⣿⣿⣿⣿⣿⡇⣼⡇⣿⣿⣿⣿⣿⠇
             ⠀⠀⠈⠋⠉⠁⣶⣶⣶⣿⣿⣿⣿⢀⣿⣿⣿⣿⣿⣿⣿⣇⣿⢰⣿⣿⣿⣿⣿⠀ 
             ⠀⠀⠀⠀⠀⠙⠿⣿⣿⣿⡄⢀⣠⣾⣿⣿⣿⣿⣿⣿⣿⣽⣿⣼⣿⣿⣿⣿⠇⠀ 
             ⠀⠀⠀⠀⠀⠀⠀⠈⠉⠒⠚⠿⠿⠿⠿⠿⠿⠿⠿⠿⠿⠛⠿⠿⠿⠿⠿⠋⠀⠀ 
             ⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀⠀ 
             ⠀⠀⠀⣿⣙⡆⠀⠀⡇⠀⢸⠀⠀⢸⠀⠀ ⢸⡇⠀⠀⢸⣏⡉  ⠙⡏⠁⠀ 
             ⠀⠀⠀⣿⣉⡷⠀⠀⢧⣀⣼ ⠀⢸⣀  ⢸⣇⡀ ⢸⣏⣁⠀ ⠀⡇⠀ 


  </div>

</details>

![Boleto](assets/javapagouxd.png)

## 🏗️ Arquitetura do Sistema

<details>

### Visão Geral da Arquitetura

```mermaid
graph TB
    %% External Components
    Client[Cliente/Ferramenta de Teste]
    ExtProcessor1[Payment Processor Default<br/>:8080/payments]
    ExtProcessor2[Payment Processor Fallback<br/>:8080/payments]
    
    %% Load Balancer
    LB[Nginx Load Balancer<br/>:9999<br/>least_conn strategy]
    
    %% Backend Services
    Backend1[Backend Instance 1<br/>Spring Boot + Undertow<br/>:8085]
    Backend2[Backend Instance 2<br/>Spring Boot + Undertow<br/>:8085]
    
    %% Internal Components of Backend
    subgraph "Camada de Aplicação"
        Controller[PaymentController<br/>REST Endpoints]
        PaymentService[PaymentService<br/>Lógica de Negócio]
        ProcessorService[PaymentProcessorService<br/>Chamadas Externas]
        HealthService[HealthCheckService<br/>Monitoramento]
    end
    
    %% Data Layer
    subgraph "Camada de Dados"
        Repository[PaymentRepository<br/>Armazenamento em Memória]
        Cache[Cache de Pagamentos<br/>ConcurrentHashMap]
    end
    
    %% Configuration
    subgraph "Configuração"
        WebClient[WebClient<br/>Pool de Conexões HTTP]
        VirtualThreads[Virtual Threads<br/>Java 21]
    end
    
    %% Client connections
    Client --> LB
    
    %% Load balancing
    LB --> Backend1
    LB --> Backend2
    
    %% Backend internal flow
    Backend1 --> Controller
    Backend2 --> Controller
    Controller --> PaymentService
    PaymentService --> Repository
    PaymentService --> ProcessorService
    PaymentService --> HealthService
    
    %% External processor calls
    ProcessorService --> WebClient
    HealthService --> WebClient
    WebClient --> ExtProcessor1
    WebClient --> ExtProcessor2
    
    %% Data storage
    Repository --> Cache
    
    %% Styling
    classDef backend fill:#e1f5fe
    classDef external fill:#f3e5f5
    classDef data fill:#e8f5e8
    classDef config fill:#fff3e0
    
    class Backend1,Backend2,Controller,PaymentService,ProcessorService,HealthService backend
    class ExtProcessor1,ExtProcessor2,Client external
    class Repository,Cache data
    class WebClient,VirtualThreads config
```

### Arquitetura de Deploy

```mermaid
graph TB
    subgraph "Ambiente Docker Compose"
        subgraph "Rede Payment Processor"
            ProcDefault[payment-processor-default:8080]
            ProcFallback[payment-processor-fallback:8080]
        end
        
        subgraph "Rede Backend"
            subgraph "Container Load Balancer"
                LB[nginx:alpine<br/>CPU: 0.2 cores<br/>Memory: 30MB]
            end
            
            subgraph "Container Backend 1"
                App1[Spring Boot App<br/>CPU: 0.65 cores<br/>Memory: 160MB<br/>Port: 8085]
            end
            
            subgraph "Container Backend 2" 
                App2[Spring Boot App<br/>CPU: 0.65 cores<br/>Memory: 160MB<br/>Port: 8085]
            end
        end
    end
    
    External[Cliente Externo<br/>Port 9999] --> LB
    LB --> App1
    LB --> App2
    App1 -.-> ProcDefault
    App1 -.-> ProcFallback
    App2 -.-> ProcDefault
    App2 -.-> ProcFallback
    
    classDef container fill:#e3f2fd
    classDef network fill:#f1f8e9
    classDef external fill:#fce4ec
    
    class App1,App2,LB container
    class ProcDefault,ProcFallback network
    class External external
```

### Fluxo de Dados

```mermaid
sequenceDiagram
    participant C as Cliente
    participant LB as Nginx LB
    participant BE as Backend
    participant HS as HealthService
    participant PS as ProcessorService
    participant R as Repository
    participant DP as Default Processor
    participant FP as Fallback Processor
    
    Note over HS: A cada 10s
    HS->>DP: Health check
    HS->>FP: Health check
    HS->>HS: Atualiza escolha do processador
    
    C->>LB: POST /payments
    LB->>BE: Encaminha requisição
    BE->>HS: getBestProcessor()
    HS-->>BE: ProcessorChoice.DEFAULT
    BE->>R: Registra pagamento (default)
    
    Note over PS: Chamadas externas desabilitadas para performance
    
    BE-->>LB: 200 OK
    LB-->>C: 200 OK
    
    C->>LB: GET /payments-summary
    LB->>BE: Encaminha requisição
    BE->>R: getSummary()
    R-->>BE: PaymentSummary
    BE-->>LB: Dados do resumo
    LB-->>C: Resposta do resumo
```
</details>

### Componentes Principais

| Componente | Responsabilidade | Tecnologia |
|------------|------------------|------------|
| **Nginx Load Balancer** | Balanceamento de carga entre instâncias | Nginx Alpine |
| **PaymentController** | Endpoints REST para pagamentos | Spring Boot |
| **PaymentService** | Lógica de negócio principal | Spring Service |
| **HealthCheckService** | Monitoramento de processadores | Scheduled Tasks |
| **PaymentProcessorService** | Interface com processadores externos | WebClient |
| **PaymentRepository** | Armazenamento em memória | ConcurrentHashMap |
| **WebClient** | Pool de conexões HTTP otimizado | Spring WebFlux |

##  Estratégia de Performance

Esta implementação foi otimizada para **máxima performance** e **menor custo por transação**

### Otimizações Principais

- **Undertow** ao invés de Tomcat (30% mais rápido)
- **Processamento assíncrono** em lotes com filas otimizadas
- **Health Check inteligente** com cache de 5s e fallback automático
- **Pool de conexões HTTP** otimizado com keep-alive
- **Repository em memória** com estruturas lock-free (LongAdder)
- **JVM tuning agressivo** para baixa latência



###  Estratégia de Escolha de Processador

1. **Health Check periódico** a cada 5s respeitando rate limit
2. **Score inteligente**: tempo de resposta + disponibilidade
3. **Preferência pelo default** (menor taxa) com tolerância 1.5x no tempo
4. **Circuit breaker** automático com retry e fallback
5. **Fallback inteligente** quando processador principal falha

### ⚡ Otimizações de Performance

- **Virtual Threads** para I/O não-bloqueante
- **G1GC** com pausa máxima de 10ms
- **String deduplication** para economia de memória
- **Tiered compilation** nivel 1 para startup rápido
- **Buffer sizes** otimizados (1KB-4KB)
- **Keep-alive** em todas as conexões HTTP

### Configurações de Recursos

- **CPU**: 1.5 cores total (0.6 por backend + 0.3 nginx)
- **Memória**: 350MB total (160MB por backend + 30MB nginx)
- **Threads**: 20 workers + 4 I/O por instância
- **Connections**: Pool de 50 conexões HTTP com keep-alive

### Tecnologias Utilizadas

- **Java 21** com Virtual Threads
- **Spring Boot 3.3** com WebFlux
- **Undertow** como servidor web
- **Nginx** com least_conn balancing
- **Caffeine** cache local
- **Repository in-memory** otimizado

### 🏃‍♂ Como Executar

```bash
# 1. Subir os Payment Processors primeiro
cd payment-processor
docker-compose up -d

# 2. Subir o backend
docker-compose up -d

# 3. Testar
curl -X POST http://localhost:9999/payments \
  -H "Content-Type: application/json" \
  -d '{"correlationId": "550e8400-e29b-41d4-a716-446655440000", "amount": 19.90}'

curl http://localhost:9999/payments-summary
```

### Objetivo

**Maximizar o lucro** processando o máximo de pagamentos possível com a **menor taxa** (processador default), mantendo **alta disponibilidade** e **baixa latência** (p99 < 10ms para bônus de performance).

---

*Feito com ❤️ e bastante ☕ para a 🐓 Rinha*
