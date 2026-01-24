package sparkintech.chat.api.dto;

import jakarta.validation.constraints.NotBlank;

public record WsChatRequest(
        String model,
        @NotBlank String prompt) {
}