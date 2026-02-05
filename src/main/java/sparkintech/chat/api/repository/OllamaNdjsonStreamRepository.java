package sparkintech.chat.api.repository;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

@Repository
public class OllamaNdjsonStreamRepository implements LlmStreamRepository {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public OllamaNdjsonStreamRepository(
            ObjectMapper mapper,
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.timeout-ms}") long timeoutMs) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public void streamChat(String model, String prompt, Consumer<String> onDelta, Runnable onDone) {
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", true,
                    "messages", new Object[] { Map.of("role", "user", "content", prompt) }));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/x-ndjson")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<java.io.InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new RuntimeException("Ollama HTTP " + resp.statusCode());
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank())
                        continue;

                    JsonNode node = mapper.readTree(line);

                    boolean done = node.path("done").asBoolean(false);

                    // For /api/chat chunks, content is typically under message.content
                    String delta = node.path("message").path("content").asText("");

                    if (!delta.isEmpty())
                        onDelta.accept(delta);

                    if (done) {
                        onDone.run();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e);
            System.out.println(e.getStackTrace());
            throw new RuntimeException("Streaming from Ollama failed: " + e.getMessage(), e);
        }
    }
}
