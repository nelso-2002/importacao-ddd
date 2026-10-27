package io.github.nelso2002.importacao.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Caixa-preta: sobe o docker-compose.yml da raiz (Keycloak, os dois serviços, os dois Postgres, o Kafka e o nginx)
 * e usa o sistema só por HTTP, pelo mesmo endereço que o navegador usa, com tokens de verdade emitidos pelo Keycloak.
 * Não conhece classe nenhuma dos serviços: se passar aqui, passa para o usuário.
 *
 * É o único teste em que assinatura, emissor e audiência dos tokens são conferidos de fato
 * (nos testes dos serviços, o jwt()/mockJwt() do spring-security-test pula essa parte).
 *
 * Usa as portas fixas do compose (4200, 8080, 8081, 8180, 9092): não rode com o compose de desenvolvimento no ar.
 */
class FluxoCompletoE2ETest {

    private static final Path RAIZ_DO_REPOSITORIO = Path.of("..").toAbsolutePath().normalize();
    // projeto próprio: containers e volumes separados do "docker compose up" do dia a dia
    private static final String PROJETO = "importacao-e2e";
    private static final String WEB = "http://localhost:4200";
    private static final String KEYCLOAK_TOKEN = "http://localhost:8180/realms/importacao/protocol/openid-connect/token";

    private static final HttpClient http = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.builder().build();

    private static String operador;
    private static String consulta;

    @BeforeAll
    static void subirOSistema() throws Exception {
        // --wait espera os healthchecks (Keycloak, bancos, Kafka e os dois serviços) ficarem saudáveis
        compose("up", "-d", "--build", "--wait", "--wait-timeout", "300");
        // o web não tem healthcheck: espera o nginx responder
        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().until(() -> get("/", null).statusCode() == 200);
        operador = token("operador", "operador");
        consulta = token("consulta", "consulta");
    }

    @AfterAll
    static void derrubarOSistema() throws Exception {
        // guarda os logs antes do down: depois dele os containers não existem mais (a CI anexa o arquivo se falhar)
        var logs = new ProcessBuilder("docker", "compose", "-p", PROJETO, "logs", "--no-color", "--timestamps")
                .directory(RAIZ_DO_REPOSITORIO.toFile())
                .redirectErrorStream(true)
                .redirectOutput(Path.of("target", "compose.log").toFile())
                .start();
        logs.waitFor(1, TimeUnit.MINUTES);
        compose("down", "-v");   // -v: cada execução começa com bancos e tópico vazios
    }

    @Test
    void comandoChegaAoEventStoreEPeloKafkaAProjecao() throws Exception {
        var referencia = "E2E-" + UUID.randomUUID().toString().substring(0, 8);

        var aberto = post("/api/processos", operador, """
                {"importador": "11222333000181", "referencia": "%s"}
                """.formatted(referencia));
        assertThat(aberto.statusCode()).isEqualTo(201);
        var id = json.readTree(aberto.body()).get("id").asString();

        var item = post("/api/processos/" + id + "/itens", operador, """
                {"ncm": "90308410", "quantidade": 3, "valor": 4500.75, "moeda": "EUR"}
                """);
        assertThat(item.statusCode()).isEqualTo(201);

        // event_store (processo-service): síncrono, já está lá quando o POST responde
        var historico = json.readTree(get("/api/processos/" + id + "/historico", consulta).body());
        assertThat(tipos(historico)).containsExactly("ProcessoAberto", "ItemAdicionado");

        // projeção (consulta-service): só chega pelo caminho outbox → Kafka → consumidor, que é assíncrono
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var resposta = get("/api/consultas/processos/" + id, consulta);
            assertThat(resposta.statusCode()).isEqualTo(200);
            var detalhe = json.readTree(resposta.body());
            assertThat(detalhe.get("referencia").asString()).isEqualTo(referencia);
            assertThat(detalhe.get("itens")).hasSize(1);
            assertThat(detalhe.get("itens").get(0).get("moeda").asString()).isEqualTo("EUR");
        });
    }

    @Test
    void streamSseAvisaDaMudancaPeloNginx() throws Exception {
        var referencia = "E2E-SSE-" + UUID.randomUUID().toString().substring(0, 8);
        var linhas = new CopyOnWriteArrayList<String>();

        // Um cliente só para o stream. Não é try-with-resources de propósito: o close() espera as requisições
        // em andamento terminarem, e um stream SSE nunca termina. O shutdownNow() corta a conexão na hora.
        var cliente = HttpClient.newHttpClient();
        try {
            var stream = HttpRequest.newBuilder(URI.create(WEB + "/api/consultas/processos/stream"))
                    .header("Authorization", "Bearer " + consulta)
                    .build();
            cliente.sendAsync(stream, HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(resposta -> resposta.body().forEach(linhas::add));
            // o keep-alive sai na hora: garante que o stream está aberto antes de provocar a mudança
            await().atMost(Duration.ofSeconds(10)).until(() -> linhas.contains(":keep-alive"));

            post("/api/processos", operador, """
                    {"importador": "12ABC34501DE35", "referencia": "%s"}
                    """.formatted(referencia));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> linhas.stream().anyMatch(linha -> linha.startsWith("data:") && linha.contains(referencia)));
            assertThat(linhas).contains("event:processo");
        } finally {
            cliente.shutdownNow();
        }
    }

    // Segurança com tokens reais: assinatura, emissor (localhost:8180) e audiência (importacao-api) conferidos.

    @Test
    void semTokenNenhumServicoResponde() throws Exception {
        assertThat(post("/api/processos", null, "{}").statusCode()).isEqualTo(401);
        assertThat(get("/api/consultas/processos", null).statusCode()).isEqualTo(401);
    }

    @Test
    void consultaLeMasNaoEscreve() throws Exception {
        assertThat(get("/api/consultas/processos", consulta).statusCode()).isEqualTo(200);
        assertThat(post("/api/processos", consulta, """
                {"importador": "11222333000181", "referencia": "E2E-PROIBIDO"}
                """).statusCode()).isEqualTo(403);
    }

    @Test
    void tokenAdulteradoERecusado() throws Exception {
        // troca a assinatura: o conteúdo continua dizendo OPERADOR, mas a chave pública do Keycloak não confere
        var partes = operador.split("\\.");
        var adulterado = partes[0] + "." + partes[1] + "." + new StringBuilder(partes[2]).reverse();

        assertThat(post("/api/processos", adulterado, """
                {"importador": "11222333000181", "referencia": "E2E-ADULTERADO"}
                """).statusCode()).isEqualTo(401);
    }

    /** Login direto com usuário e senha (cliente importacao-cli), só para testes: o navegador usa o fluxo com PKCE. */
    private static String token(String usuario, String senha) throws IOException, InterruptedException {
        var formulario = Map.of(
                        "grant_type", "password",
                        "client_id", "importacao-cli",
                        "username", usuario,
                        "password", senha)
                .entrySet().stream()
                .map(campo -> campo.getKey() + "=" + URLEncoder.encode(campo.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        var resposta = http.send(HttpRequest.newBuilder(URI.create(KEYCLOAK_TOKEN))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formulario))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resposta.statusCode()).as("login de %s no Keycloak: %s", usuario, resposta.body()).isEqualTo(200);
        return json.readTree(resposta.body()).get("access_token").asString();
    }

    private static List<String> tipos(JsonNode historico) {
        return historico.valueStream().map(evento -> evento.get("tipo").asString()).toList();
    }

    private static HttpResponse<String> get(String caminho, String token) throws IOException, InterruptedException {
        return http.send(requisicao(caminho, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String caminho, String token, String corpo) throws IOException, InterruptedException {
        var requisicao = requisicao(caminho, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder requisicao(String caminho, String token) {
        var requisicao = HttpRequest.newBuilder(URI.create(WEB + caminho));
        return token == null ? requisicao : requisicao.header("Authorization", "Bearer " + token);
    }

    private static void compose(String... argumentos) throws Exception {
        var comando = Stream.concat(Stream.of("docker", "compose", "-p", PROJETO), Stream.of(argumentos)).toList();
        var processo = new ProcessBuilder(comando).directory(RAIZ_DO_REPOSITORIO.toFile()).inheritIO().start();
        if (!processo.waitFor(10, TimeUnit.MINUTES) || processo.exitValue() != 0) {
            throw new IllegalStateException("Falhou: " + String.join(" ", comando));
        }
    }
}
