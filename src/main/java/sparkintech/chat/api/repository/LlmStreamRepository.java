package sparkintech.chat.api.repository;

import java.util.function.Consumer;

public interface LlmStreamRepository {
  void streamChat(String model, String prompt, Consumer<String> onDelta, Runnable onDone);
}