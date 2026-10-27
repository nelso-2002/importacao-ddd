package io.github.nelso2002.importacao.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Caixa-preta: sobe o docker-compose.yml da raiz (os dois serviços, os dois Postgres, o Kafka e o nginx)
 * e usa o sistema só por HTTP, pelo mesmo endereço que o navegador usa.
 * Não conhece classe nenhuma dos serviços: se passar aqui, passa para o usuário.
 *
 * Usa as portas fixas do compose (4200, 8080, 8081, 9092): não rode com o compose de desenvolvimento no ar.
 */
class FluxoCompletoE2ETest {

    private static final Path RAIZ_DO_REPOSITORIO = Path.of("..").toAbsolutePath().normalize();
    // projeto próprio: containers e volumes separados do "docker compose up" do dia a dia
    private static final String PROJETO = "importacao-e2e";
    private static final String WEB = "http://localhost:4200";

    private static final HttpClient http = HttpClient.newHttpClient();
    private static final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void subirOSistema() throws Exception {
        // --wait espera os healthchecks (bancos, Kafka e os dois serviços) ficarem saudáveis
        compose("up", "-d", "--build", "--wait", "--wait-timeout", "300");
        // o web não tem healthcheck: espera o nginx responder
        await().atMost(Duration.ofSeconds(60)).ignoreExceptions().until(() -> get("/").statusCode() == 200);
    }

    @AfterAll
    static void derrubarOSistema() throws Exception {
        compose("down", "-v");   // -v: cada execução começa com bancos e tópico vazios
    }

    @Test
    void comandoChegaAoEventStoreEPeloKafkaAProjecao() throws Exception {
        var referencia = "E2E-" + UUID.randomUUID().toString().substring(0, 8);

        var aberto = post("/api/processos", """
                {"importador": "11222333000181", "referencia": "%s"}
                """.formatted(referencia));
        assertThat(aberto.statusCode()).isEqualTo(201);
        var id = json.readTree(aberto.body()).get("id").asString();

        var item = post("/api/processos/" + id + "/itens", """
                {"ncm": "90308410", "quantidade": 3, "valor": 4500.75, "moeda": "EUR"}
                """);
        assertThat(item.statusCode()).isEqualTo(201);

        // event_store (processo-service): síncrono, já está lá quando o POST responde
        var historico = json.readTree(get("/api/processos/" + id + "/historico").body());
        assertThat(tipos(historico)).containsExactly("ProcessoAberto", "ItemAdicionado");

        // projeção (consulta-service): só chega pelo caminho outbox → Kafka → consumidor, que é assíncrono
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var resposta = get("/api/consultas/processos/" + id);
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
            cliente.sendAsync(HttpRequest.newBuilder(URI.create(WEB + "/api/consultas/processos/stream")).build(),
                            HttpResponse.BodyHandlers.ofLines())
                    .thenAccept(resposta -> resposta.body().forEach(linhas::add));
            // o keep-alive sai na hora: garante que o stream está aberto antes de provocar a mudança
            await().atMost(Duration.ofSeconds(10)).until(() -> linhas.contains(":keep-alive"));

            post("/api/processos", """
                    {"importador": "12ABC34501DE35", "referencia": "%s"}
                    """.formatted(referencia));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> linhas.stream().anyMatch(linha -> linha.startsWith("data:") && linha.contains(referencia)));
            assertThat(linhas).contains("event:processo");
        } finally {
            cliente.shutdownNow();
        }
    }

    private static List<String> tipos(JsonNode historico) {
        return historico.valueStream().map(evento -> evento.get("tipo").asString()).toList();
    }

    private static HttpResponse<String> get(String caminho) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create(WEB + caminho)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String caminho, String corpo) throws IOException, InterruptedException {
        var requisicao = HttpRequest.newBuilder(URI.create(WEB + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private static void compose(String... argumentos) throws Exception {
        var comando = Stream.concat(Stream.of("docker", "compose", "-p", PROJETO), Stream.of(argumentos)).toList();
        var processo = new ProcessBuilder(comando).directory(RAIZ_DO_REPOSITORIO.toFile()).inheritIO().start();
        if (!processo.waitFor(10, TimeUnit.MINUTES) || processo.exitValue() != 0) {
            throw new IllegalStateException("Falhou: " + String.join(" ", comando));
        }
    }
}
