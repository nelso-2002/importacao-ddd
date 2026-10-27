# Processos de Importação — DDD, Event Sourcing, CQRS e Kafka

[![CI](https://github.com/nelso-2002/importacao-ddd/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/nelso-2002/importacao-ddd/actions/workflows/ci.yml)

Projeto de estudo: um sistema de processos de importação construído em etapas, com Java 25, Spring Boot 4, Angular 22, Postgres e Kafka.

## Como rodar

Precisa só de Docker com Compose v2.

```bash
git clone https://github.com/nelso-2002/importacao-ddd.git
cd importacao-ddd
docker compose up -d --build
```

Abra **http://localhost:4200** e entre com `operador`/`operador` (pode tudo) ou `consulta`/`consulta` (só lê). A primeira subida compila tudo dentro do Docker e leva alguns minutos; as seguintes usam o cache.

Para parar: `docker compose down` (os dados ficam nos volumes; `docker compose down -v` apaga tudo).

## Arquitetura

```
 navegador ──► web (nginx :4200): só os arquivos da tela
     │  login (PKCE) ──► keycloak :8180 ──► token JWT
     │
     ├── Bearer ──► processo-service :8080 ──► postgres-processo (event_store + outbox)
     │                                    │ publica (Outbox)
     │                                    ▼
     └── Bearer ──► consulta-service :8081 ◄── kafka: processo-eventos
                    (WebFlux, SSE)      └──► postgres-consulta (processo_resumo)
```

| Serviço | Papel | Tecnologia |
|---|---|---|
| `processo-service` | Comandos e regras de negócio; grava eventos (Event Sourcing) e os publica pelo padrão Outbox | Spring MVC, JDBC, virtual threads |
| `consulta-service` | Consome os eventos, mantém a projeção de leitura no próprio banco e transmite mudanças ao vivo | Spring WebFlux, R2DBC, Server-Sent Events |
| `web` | Interface: lista ao vivo, detalhe com histórico, formulários | Angular 22 (zoneless, signals), nginx |
| `keycloak` | Login e emissão de tokens; papéis `OPERADOR` (escreve) e `CONSULTA` (só lê) | Keycloak 26, OIDC |

Os dois serviços não compartilham banco: só se comunicam pelo tópico `processo-eventos`. A tela chama cada serviço direto, com o token no cabeçalho, e cada serviço libera a origem da tela via CORS. Segurança em detalhe: [SEGURANCA.md](SEGURANCA.md).

## Endereços

| O quê | Onde |
|---|---|
| Tela | http://localhost:4200 |
| Comandos e histórico | `POST /processos`, `POST /processos/{id}/itens`, `GET /processos/{id}/historico` em http://localhost:8080 |
| Consultas | `GET /consultas/processos`, `GET /consultas/processos/{id}`, `GET /consultas/processos/stream` (SSE) em http://localhost:8081 |
| Keycloak | http://localhost:8180 (admin: `admin`/`admin`) |
| Kafka (da máquina) | `localhost:9092` |

## Desenvolvimento

Versões de Java e Node no [`mise.toml`](mise.toml). Cada serviço tem um `compose.yaml` próprio que o Spring Boot sobe sozinho:

```bash
(cd processo-service && ./mvnw spring-boot:run)   # sobe o Postgres dele, o Kafka e o Keycloak
(cd consulta-service && ./mvnw spring-boot:run)   # sobe o Postgres dele; usa o Kafka e o Keycloak acima
(cd web && npx ng serve)                          # http://localhost:4200
```

Não rode ao mesmo tempo que o `docker compose` da raiz: os dois usam as portas 4200, 8080, 8081, 8180 e 9092.

Testes (precisam de Docker, por causa do Testcontainers):

```bash
(cd processo-service && ./mvnw test)
(cd consulta-service && ./mvnw test)
(cd web && npx ng test --watch=false)
(cd e2e && ./mvnw test)                  # sobe o docker-compose da raiz e testa o fluxo completo
```

A CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) roda tudo isso a cada push.

## Etapas

| Etapa | Conteúdo | PR |
|---|---|---|
| 1 | Domínio DDD, casos de uso, JPA e API REST | [#1](https://github.com/nelso-2002/importacao-ddd/pull/1) |
| 2 | Event Sourcing e CQRS | [#2](https://github.com/nelso-2002/importacao-ddd/pull/2) |
| 3 | `consulta-service` reativo e front-end Angular | [#3](https://github.com/nelso-2002/importacao-ddd/pull/3) |
| 4 | Outbox, Kafka, banco por serviço e Docker Compose | [#4](https://github.com/nelso-2002/importacao-ddd/pull/4) |
| 5 | Testes de ponta a ponta, CI e segurança (Keycloak/JWT) | em andamento |
