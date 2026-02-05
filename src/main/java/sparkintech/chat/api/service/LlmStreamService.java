package sparkintech.chat.api.service;


import sparkintech.chat.api.repository.LlmStreamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class LlmStreamService {

    private final LlmStreamRepository repo;
    private final String defaultModel;

    public LlmStreamService(LlmStreamRepository repo,
            @Value("${ollama.model}") String defaultModel) {
        this.repo = repo;
        this.defaultModel = defaultModel;
    }

    public void streamChat(String modelOrNull, String prompt, Consumer<String> onDelta, Runnable onDone) {
        try {
            String model = (modelOrNull == null || modelOrNull.isBlank()) ? defaultModel : modelOrNull;
            repo.streamChat(model, prompt, onDelta, onDone);
        } catch(Error e) {
                System.out.println("Streaming from Ollama failed: " + e); // prints stacktrace
                throw new RuntimeException("Streaming from Ollama failed: " + e.getClass().getName(), e);
              
              
        }
    }
}
