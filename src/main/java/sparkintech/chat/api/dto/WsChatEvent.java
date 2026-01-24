package sparkintech.chat.api.dto;

public record WsChatEvent(
        String type, // "delta" | "done" | "error"
        String content, // for "delta"
        String message // for "error"
) {
    public static WsChatEvent delta(String content) {
        return new WsChatEvent("delta", content, null);
    }

    public static WsChatEvent done() {
        return new WsChatEvent("done", null, null);
    }

    public static WsChatEvent error(String message) {
        return new WsChatEvent("error", null, message);
    }
}