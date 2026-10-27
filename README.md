# Processos de Importação — DDD, Event Sourcing, CQRS e Kafka

Projeto de estudo: um sistema de processos de importação construído em etapas, com Java 25, Spring Boot 4, Angular 22, Postgres e Kafka.

## Como rodar

Precisa só de Docker com Compose v2.

```bash
git clone https://github.com/nelso-2002/importacao-ddd.git
cd importacao-ddd
docker compose up -d --build
```

Abra **http://localhost:4200**. A primeira subida compila tudo dentro do Docker e leva alguns minutos; as seguintes usam o cache.

Para parar: `docker compose down` (os dados ficam nos volumes; `docker compose down -v` apaga tudo).

## Arquitetura

```
 navegador ──► web (nginx :4200) ──/api/processos──► processo-service :8080 ──► postgres-processo
                     │                                  event_store + outbox
                     │                                         │ publica (Outbox)
                     │                                         ▼
                     └────────/api/consultas──► consulta-service :8081 ◄── kafka: processo-eventos
                                                (WebFlux, SSE)    └──► postgres-consulta (processo_resumo)
```

| Serviço | Papel | Tecnologia |
|---|---|---|
| `processo-service` | Comandos e regras de negócio; grava eventos (Event Sourcing) e os publica pelo padrão Outbox | Spring MVC, JDBC, virtual threads |
| `consulta-service` | Consome os eventos, mantém a projeção de leitura no próprio banco e transmite mudanças ao vivo | Spring WebFlux, R2DBC, Server-Sent Events |
| `web` | Interface: lista ao vivo, detalhe com histórico, formulários | Angular 22 (zoneless, signals), nginx |

Os dois serviços não compartilham banco: só se comunicam pelo tópico `processo-eventos`.

## Endereços

| O quê | Onde |
|---|---|
| Tela | http://localhost:4200 |
| Comandos e histórico | `POST /processos`, `POST /processos/{id}/itens`, `GET /processos/{id}/historico` em http://localhost:8080 |
| Consultas | `GET /consultas/processos`, `GET /consultas/processos/{id}`, `GET /consultas/processos/stream` (SSE) em http://localhost:8081 |
| Kafka (da máquina) | `localhost:9092` |

## Desenvolvimento

Versões de Java e Node no [`mise.toml`](mise.toml). Cada serviço tem um `compose.yaml` próprio que o Spring Boot sobe sozinho:

```bash
(cd processo-service && ./mvnw spring-boot:run)   # sobe o Postgres dele e o Kafka
(cd consulta-service && ./mvnw spring-boot:run)   # sobe o Postgres dele; usa o Kafka acima
(cd web && npx ng serve)                          # http://localhost:4200, com proxy para os dois
```

Não rode ao mesmo tempo que o `docker compose` da raiz: os dois usam as portas 8080, 8081, 9092 e 4200.

Testes (precisam de Docker, por causa do Testcontainers):

```bash
(cd processo-service && ./mvnw test)
(cd consulta-service && ./mvnw test)
(cd web && npx ng test --watch=false)
```

## Etapas

| Etapa | Conteúdo | PR |
|---|---|---|
| 1 | Domínio DDD, casos de uso, JPA e API REST | [#1](https://github.com/nelso-2002/importacao-ddd/pull/1) |
| 2 | Event Sourcing e CQRS | [#2](https://github.com/nelso-2002/importacao-ddd/pull/2) |
| 3 | `consulta-service` reativo e front-end Angular | [#3](https://github.com/nelso-2002/importacao-ddd/pull/3) |
| 4 | Outbox, Kafka, banco por serviço e Docker Compose | — |
