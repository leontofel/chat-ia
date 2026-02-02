package sparkintech.chat.api.store;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatHistoryStore {
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> history = new ConcurrentHashMap<>();

    public void append(String sessionId, String entry) {
        history.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(entry);
    }

    public List<String> get(String sessionId) {
        return history.getOrDefault(sessionId, new CopyOnWriteArrayList<>());
    }
}
