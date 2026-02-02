package sparkintech.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import sparkintech.chat.api.service.LlmStreamService;
import sparkintech.chat.api.store.ChatHistoryStore;

import java.util.concurrent.*;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final LlmStreamService service;
    private final ObjectMapper mapper;
    private final ChatHistoryStore store;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Future<?>> running = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(LlmStreamService service, ObjectMapper mapper, ChatHistoryStore store) {
        this.service = service;
        this.mapper = mapper;
        this.store = store;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = mapper.readTree(message.getPayload());
        String type = root.path("type").asText("chat");

        if ("cancel".equals(type)) {
            cancelJob(session);
            safeSend(session, mapper.createObjectNode().put("type", "cancelled"));
            return;
        }

        // default: chat
        String sessionId = root.path("sessionId").asText("default");
        String model = root.path("model").asText(null);
        String prompt = root.path("prompt").asText("");

        if (prompt.isBlank()) {
            safeSend(session, mapper.createObjectNode().put("type", "error").put("message", "prompt is empty"));
            return;
        }

        // cancel previous stream for this socket session
        cancelJob(session);

        Future<?> job = executor.submit(() -> {
            try {
                store.append(sessionId, "USER: " + prompt);

                StringBuilder full = new StringBuilder();

                service.streamChat(model, prompt,
                        delta -> {
                            full.append(delta);
                            safeSend(session, mapper.createObjectNode().put("type", "delta").put("content", delta));
                        },
                        () -> {
                            store.append(sessionId, "AI: " + full);
                            safeSend(session, mapper.createObjectNode().put("type", "done"));
                        });
            } catch (Exception e) {
                safeSend(session, mapper.createObjectNode().put("type", "error").put("message", e.getMessage()));
            }
        });

        running.put(session.getId(), job);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cancelJob(session);
    }

    private void cancelJob(WebSocketSession session) {
        Future<?> prev = running.remove(session.getId());
        if (prev != null)
            prev.cancel(true);
    }

    private void safeSend(WebSocketSession session, JsonNode node) {
        try {
            if (!session.isOpen())
                return;
            String json = node.toString();
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {
        }
    }
}
