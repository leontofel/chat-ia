package sparkintech.chat;

import com.fasterxml.jackson.databind.ObjectMapper;

import sparkintech.chat.api.dto.WsChatEvent;
import sparkintech.chat.api.dto.WsChatRequest;
import sparkintech.chat.api.service.LlmStreamService;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.*;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final LlmStreamService service;
    private final ObjectMapper mapper;

    // One worker thread per request is fine to start; later you can tune pooling.
    private final ExecutorService executor = Executors.newCachedThreadPool();

    // Optional: allow only 1 active stream per websocket session
    private final ConcurrentHashMap<String, Future<?>> running = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(LlmStreamService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Cancel any previous stream on this same WS session
        Future<?> prev = running.remove(session.getId());
        if (prev != null)
            prev.cancel(true);

        WsChatRequest req = mapper.readValue(message.getPayload(), WsChatRequest.class);

        Future<?> job = executor.submit(() -> {
            try {
                service.streamChat(req.model(), req.prompt(),
                        delta -> safeSend(session, WsChatEvent.delta(delta)),
                        () -> safeSend(session, WsChatEvent.done()));
            } catch (Exception e) {
                safeSend(session, WsChatEvent.error(e.getMessage()));
            }
        });

        running.put(session.getId(), job);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Future<?> job = running.remove(session.getId());
        if (job != null)
            job.cancel(true);
    }

    private void safeSend(WebSocketSession session, WsChatEvent event) {
        try {
            if (!session.isOpen())
                return;
            String json = mapper.writeValueAsString(event);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {
            // client likely disconnected
        }
    }
}
