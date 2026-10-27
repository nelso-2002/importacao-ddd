# Segurança — revisão contra o OWASP Top 10 (2025)

Revisão do código deste repositório contra as dez categorias do [OWASP Top 10:2025](https://owasp.org/Top10/).
Para cada uma: o que o projeto faz, onde está no código, e o que fica de fora (por ser um ambiente de estudo
ou por decisão consciente). "Fica de fora" não é esquecimento: é a lista do que mudaria antes de produção.

## Como a autenticação funciona

```
navegador ──(1) login no Keycloak, Authorization Code + PKCE──► Keycloak :8180
          ◄─(2) access token JWT (5 min), assinado com a chave privada do realm
          ──(3) Authorization: Bearer <token> ──► processo-service :8080 / consulta-service :8081
                                                  conferem assinatura (chave pública via JWKS),
                                                  emissor (iss), audiência (aud) e validade;
                                                  papéis de realm_access.roles → ROLE_*
```

Usuários de teste do realm: `operador`/`operador` (OPERADOR e CONSULTA) e `consulta`/`consulta` (só CONSULTA).

## As dez categorias

| # | Categoria | O que o projeto faz | Fica de fora |
|---|---|---|---|
| A01 | Broken Access Control | Toda rota exige token, exceto `/actuator/health`. Só `OPERADOR` envia comandos (`@PreAuthorize` em `ProcessoController`); `CONSULTA` só lê. Token sem papéis do sistema → 403 (falha fechada). CORS com lista fechada de origens. Ids são UUID, não sequenciais. Testes de 401/403 nos dois serviços e no e2e com tokens reais. | Não há "dono" do processo: qualquer `OPERADOR` altera qualquer processo. Se o negócio exigir (por importador, por filial), é uma regra a mais no agregado. |
| A02 | Security Misconfiguration | Actuator expõe só o `health`. Erros saem em `ProblemDetail` sem stack trace. CORS sem `*`, só os métodos e cabeçalhos usados. Containers como usuário não-root. Workflow de CI com permissão só de leitura. | Senhas fixas no `docker-compose.yml` (bancos, admin do Keycloak) e Keycloak em `start-dev`. Em produção: *secrets* e `start` com banco próprio. |
| A03 | Software Supply Chain Failures | Versões fixas: imagens Docker, `package-lock.json`, `oidc-client-ts` sem `^`, actions do GitHub fixadas por SHA. O npm 11 bloqueia *install scripts* não aprovados. | Sem verificação automática de vulnerabilidades (Dependabot, `npm audit`, OWASP Dependency-Check) na CI. |
| A04 | Cryptographic Failures | Tokens JWT assinados (RS256) e conferidos com a chave pública do Keycloak; nenhuma senha passa pelos serviços nem pela tela. | **Sem TLS em lugar nenhum**: tokens e dados trafegam em texto puro. Em produção, HTTPS em tudo (o token é um portador: quem o intercepta o usa até expirar). |
| A05 | Injection | SQL sempre com parâmetros (`JdbcClient`, `DatabaseClient`), nunca concatenando texto do usuário. Curingas do `ilike` escapados na busca (`ConsultaProcessos.literal`). Value objects validam formato na borda (CNPJ, NCM, moeda). Angular escapa toda interpolação (sem `innerHTML`). | Sem Content-Security-Policy no nginx, que limitaria o estrago de um XSS. |
| A06 | Insecure Design | Regras no domínio (o agregado recusa operações inválidas, independentemente da tela). Concorrência otimista no event store. Outbox contra o *dual write*; consumidor idempotente. | Sem limite de requisições (*rate limiting*) por usuário ou IP. |
| A07 | Authentication Failures | Login no Keycloak (a aplicação não guarda senhas). Authorization Code + PKCE no Angular (sem *implicit flow*, sem senha na tela). Token de 5 min, renovado pelo refresh token. Emissor e audiência (`importacao-api`) conferidos: token emitido para outra aplicação é recusado. | O cliente `importacao-cli` (login com senha direto) existe só para testes e não deveria existir em produção. Sem MFA (o Keycloak suporta). |
| A08 | Software or Data Integrity Failures | Assinatura do JWT conferida (teste e2e com token adulterado → 401). Event store só aceita inserção (histórico imutável). | O Kafka não tem autenticação: quem alcança a rede dele publica eventos falsos em `processo-eventos`. Em produção: SASL/ACLs no Kafka. |
| A09 | Logging & Alerting Failures | Cada evento grava quem o causou (`usuario`, via `ScopedValue`), e o histórico é imutável: "quem fez o quê, quando" vem do próprio event store. | Sem alertas nem centralização de logs; 401/403 repetidos não disparam nada. |
| A10 | Mishandling of Exceptional Conditions | Exceções traduzidas num lugar só (`ApiExceptionHandler`). Projeção que falha é retentada, nunca descartada. Publicador da outbox para no primeiro erro (não inverte a ordem). Na dúvida, nega: sem papel, 403. | `IllegalArgumentException` genérica vira 400: um bug interno que a lance apareceria como erro do cliente (marcado com `ponytail:` no código). |

## Decisões que valem explicar

- **Token no `sessionStorage`.** Some ao fechar a aba e não é compartilhado entre abas, mas um XSS conseguiria lê-lo. A alternativa mais forte é o padrão BFF (o token fica num servidor e o navegador só tem um cookie `HttpOnly`), que traz de volta a necessidade de proteção contra CSRF.
- **Sem CSRF nos serviços.** CSRF explora o cookie que o navegador envia sozinho; aqui o token vai num cabeçalho que só o código da tela consegue colocar.
- **Stream SSE com `fetch`.** O `EventSource` não envia cabeçalhos, e o token na URL iria para logs e para o histórico do navegador. O token é conferido quando o stream abre; uma conexão aberta continua depois que o token expira, até cair e reconectar com um token novo.
- **CORS em vez de proxy.** Com o proxy, tudo era a mesma origem e o navegador nunca aplicava CORS. Agora a tela chama os serviços direto, e cada um declara quais origens aceita: fora da lista, o preflight recebe 403.
