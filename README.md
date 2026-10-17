# Payment API

API REST de pagamentos em **Java 21 + Spring Boot 4**, com **idempotência**, **gateway de pagamento simulado** e **webhooks assinados com HMAC-SHA256**. Projeto de estudo focado nos problemas reais de backend financeiro: não cobrar duas vezes, manter o estado consistente e avisar o cliente de forma segura.

> Nenhum dinheiro real é processado. O gateway é simulado, e o foco do projeto é a arquitetura.

---

## Destaques técnicos

- **Idempotência** via header `Idempotency-Key`: repetir uma requisição devolve o pagamento original, sem cobrar duas vezes. Protegida também contra requisições simultâneas pela constraint `UNIQUE` do banco.
- **Máquina de estados na entidade**: `PENDING → APPROVED | FAILED`, `APPROVED → REFUNDED`. Transições inválidas lançam exceção de domínio (HTTP 409).
- **Gateway desacoplado por interface** (`PaymentGateway`): trocar o simulado por Stripe ou Mercado Pago não exige mexer na regra de negócio.
- **Webhooks assinados** (HMAC-SHA256 no header `X-Signature`), enviados de forma **assíncrona**, com **3 tentativas** em caso de falha.
- **Autenticação stateless com JWT** (Spring Security + JJWT), senhas com BCrypt.
- **Migrations versionadas com Flyway**; o Hibernate só valida o schema (`ddl-auto: validate`).
- **Dinheiro em `BigDecimal`** (`NUMERIC(19,2)`) e **UUID** como chave primária.
- **Tratamento de erros centralizado** (`@RestControllerAdvice`) com respostas padronizadas.
- **Documentação interativa** com Swagger/OpenAPI, incluindo autorização por Bearer token.
- **Testes unitários** (JUnit 5 + Mockito + AssertJ) sobre as regras críticas.
- **Fluxo de Git profissional**: branches `feature/*`, Pull Requests com squash merge para `develop` e Conventional Commits.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4 |
| Persistência | Spring Data JPA + Hibernate |
| Banco de dados | PostgreSQL |
| Migrations | Flyway |
| Segurança | Spring Security + JWT (JJWT) |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, AssertJ |
| Build | Maven |
| Utilitários | Lombok |

---

## Como rodar localmente

**Pré-requisitos:** Java 21, Maven (ou o `mvnw` incluído) e PostgreSQL rodando localmente.

```bash
# 1. Clone o repositório
git clone <url-do-repositorio>
cd payment-api

# 2. Crie o banco
psql -U postgres -c "CREATE DATABASE payment_api_dev;"

# 3. Ajuste usuário e senha, se necessário (padrão: postgres / postgres)
#    via variáveis DB_USERNAME e DB_PASSWORD, ou em src/main/resources/application-dev.yml

# 4. Suba a aplicação (o Flyway cria as tabelas sozinho)
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`, e a documentação interativa em:

```
http://localhost:8080/swagger-ui/index.html
```

### Variáveis de ambiente

| Variável | Uso | Padrão em `dev` | Em `prod` |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Profile ativo | `dev` | `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Banco de dados | `localhost` / `postgres` / `postgres` | obrigatórias |
| `JWT_SECRET` | Segredo do token (mínimo 32 caracteres) | valor de desenvolvimento | obrigatória |
| `WEBHOOK_URL` | URL do lojista que recebe os webhooks | `http://localhost:9000/webhook` | obrigatória |
| `WEBHOOK_SECRET` | Segredo da assinatura HMAC | valor de desenvolvimento | obrigatória |

Em `prod` **não há valores padrão** para credenciais e segredos: se faltar alguma variável, a aplicação não sobe.

---

## Como usar

1. **Cadastre-se** e faça **login** para obter um token:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@teste.com","password":"12345678"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@teste.com","password":"12345678"}'
```

2. **Crie um pagamento** (o header `Idempotency-Key` é obrigatório):

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer <token>" \
  -H "Idempotency-Key: pedido-001" \
  -H "Content-Type: application/json" \
  -d '{"amount":100.00,"currency":"BRL","description":"Pedido #1"}'
```

3. **Processe** o pagamento (envia ao gateway e dispara o webhook):

```bash
curl -X POST http://localhost:8080/api/payments/<id>/process \
  -H "Authorization: Bearer <token>"
```

No Swagger, use o botão **Authorize** e cole o token **sem** o prefixo `Bearer`.

---

## Endpoints

| Método | Rota | Descrição | Autenticação |
|---|---|---|---|
| `POST` | `/api/auth/register` | Cadastra um usuário | Pública |
| `POST` | `/api/auth/login` | Autentica e devolve um JWT | Pública |
| `POST` | `/api/payments` | Cria um pagamento (idempotente) | JWT |
| `GET` | `/api/payments/{id}` | Busca um pagamento | JWT |
| `POST` | `/api/payments/{id}/process` | Processa no gateway e dispara o webhook | JWT |

### Respostas de erro

| Status | Quando |
|---|---|
| `400` | Validação falhou, header `Idempotency-Key` ausente, JSON ou UUID inválido |
| `401` | Token ausente, inválido ou expirado; credenciais incorretas no login |
| `404` | Pagamento não encontrado |
| `409` | Pagamento fora do estado exigido (ex.: já processado); e-mail já cadastrado |
| `422` | `Idempotency-Key` reutilizada com um corpo diferente |
| `500` | Erro inesperado (mensagem neutra; o detalhe fica só no log) |

---

## Como funcionam as partes principais

### Idempotência

O cliente envia uma chave única por operação (`Idempotency-Key`). Se a rede falhar e ele repetir a requisição, não há cobrança duplicada.

| Situação | Resultado |
|---|---|
| Chave nova | Cria o pagamento |
| Mesma chave, mesmo corpo | Devolve o pagamento original |
| Mesma chave, corpo diferente | `422` |
| Duas requisições simultâneas com a mesma chave | A constraint `UNIQUE` do banco barra uma delas, e o serviço a trata como repetição |

### Ciclo de vida do pagamento

```
PENDING ──► APPROVED ──► REFUNDED
   │
   └──────► FAILED
```

As regras de transição ficam dentro da entidade `Payment` (`approve()`, `fail()`, `refund()`), e não espalhadas nos serviços. Não há setters públicos de status.

### Gateway

`POST /api/payments/{id}/process` chama o `PaymentGateway`. A implementação simulada é determinística: **valores acima de R$ 5.000,00 são recusados**, os demais aprovados. O status é verificado **antes** de chamar o gateway, para nunca cobrar duas vezes o mesmo pagamento.

### Webhook

Quando o pagamento é processado (aprovado ou recusado), a API faz um `POST` na URL configurada do lojista, em segundo plano:

```json
{
  "paymentId": "3f2a...",
  "status": "APPROVED",
  "amount": 100.00,
  "currency": "BRL",
  "occurredAt": "2026-10-14T18:30:00Z"
}
```

com o header `X-Signature`, que é o **HMAC-SHA256 do corpo** calculado com um segredo compartilhado. O lojista recalcula a assinatura sobre o corpo bruto recebido: se bater, a mensagem é autêntica e não foi alterada.

- **Assíncrono (`@Async`)**: um lojista lento ou fora do ar não atrasa a resposta da API.
- **Retry**: 3 tentativas com 2 segundos de intervalo; se todas falharem, o erro é registrado em log.

### Segurança

- Senhas guardadas com **BCrypt**; nunca em texto puro.
- Token JWT assinado (HS256) com expiração; sessão **stateless**.
- Login devolve a mesma mensagem para e-mail inexistente e senha errada, evitando descobrir quais e-mails estão cadastrados.
- O JWT secret precisa ter no mínimo 32 caracteres, e a aplicação não sobe com um segredo fraco.

---

## Arquitetura

```
com.gabrielf.payment_api/
├── config/        → configurações (Security, OpenAPI, propriedades do webhook)
├── controller/    → endpoints REST (só recebem, validam e delegam)
├── dto/           → records de entrada e saída (a entidade nunca vaza na API)
├── exception/     → exceções de domínio + handler global
├── gateway/       → interface PaymentGateway + implementação simulada
├── model/         → entidades JPA e enums
├── repository/    → interfaces Spring Data
├── security/      → filtro JWT, serviço de token, UserDetailsService
└── service/       → regras de negócio, webhook e assinatura
```

Fluxo de uma requisição: `Controller` → `Service` → `Repository` → `PostgreSQL`.

---

## Testes

```bash
./mvnw test
```

Testes unitários com Mockito, **sem banco e sem contexto Spring**, então rodam em milissegundos. Cobrem:

- **Criação idempotente**: chave nova, repetição com o mesmo corpo, reutilização com corpo diferente, corrida entre requisições simultâneas e propagação de erros de integridade que não vêm da chave.
- **Processamento**: aprovação, recusa, pagamento inexistente e pagamento já processado (garantindo que o gateway **não** é chamado de novo).
- **Assinatura do webhook**: determinismo, sensibilidade ao corpo e ao segredo, e formato (64 caracteres hexadecimais).
- **Cadastro de usuário**: senha persistida somente como hash e prevenção de e-mail duplicado, inclusive sob concorrência.

O filtro JWT, os controllers e o envio real do webhook (incluindo o retry) foram validados **manualmente** com Postman e webhook.site.

---

## Decisões e limitações conhecidas

Escolhas conscientes de escopo, com o que seria feito em produção:

| Limitação | Consequência | Evolução em produção |
|---|---|---|
| Webhook sem persistência das entregas | Se a aplicação cair durante o envio, ou as 3 tentativas falharem, o evento se perde | Padrão **outbox**: gravar o evento no banco junto com a mudança e reenviar até confirmar |
| Espera fixa de 2 s entre tentativas | Esgota rápido se o lojista ficar fora por minutos | **Backoff exponencial** e mais tentativas |
| Uma única URL de webhook, fixa na configuração | Atende um só lojista | Cadastro de endpoints por cliente, cada um com seu segredo |
| `process` sem controle de concorrência | Duas chamadas simultâneas ao mesmo pagamento podem passar juntas pela checagem de status | **Optimistic locking** (`@Version`) |
| Replay da criação devolve `201`, igual à criação | O cliente não distingue criado de repetido | Devolver `200` no replay |
| JWT para autenticar a API | Em produção, quem consome é outro sistema | **API keys** por lojista |
| Sem roles | Todo usuário autenticado acessa os mesmos endpoints | Perfis, se houver permissões distintas |
| Sem testes de integração | O comportamento com banco real é validado manualmente | Testcontainers |

---

## Possíveis evoluções

- Estorno (`REFUNDED`) exposto por endpoint, com chamada ao gateway
- Padrão outbox para os webhooks
- Listagem de pagamentos com paginação e filtro por status
- Rate limiting por cliente
- Testes de integração com Testcontainers
- Docker Compose para subir app e banco com um comando

---

## Fluxo de trabalho

Branches `feature/*` a partir de `develop`, Pull Request por feature com **squash merge** e mensagens no padrão **Conventional Commits** (`feat:`, `test:`, `docs:`). A `main` recebe o código estável no fechamento de cada versão.

---
