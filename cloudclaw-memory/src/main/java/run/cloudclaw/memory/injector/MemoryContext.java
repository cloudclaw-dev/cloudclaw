package run.cloudclaw.memory.injector;

import lombok.AllArgsConstructor;
import lombok.Getter;
import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of memory context building, including the formatted text
 * and references to which memory items were actually included.
 */
@Getter
@AllArgsConstructor
public class MemoryContext {

    /** The formatted memory text to inject into the system prompt */
    private final String content;

    /** Profile items that were actually included (within token budget) */
    private final List<ProfileItem> includedProfileItems;

    /** Session items that were actually included (within token budget) */
    private final List<SessionItem> includedSessionItems;

    public MemoryContext(String content) {
        this.content = content;
        this.includedProfileItems = new ArrayList<>();
        this.includedSessionItems = new ArrayList<>();
    }

    /**
     * Simple record for sending memory references to the frontend via ChatChunk.
     */
    @Getter
    @AllArgsConstructor
    public static class Ref {
        private final String type;    // "profile" or "session"
        private final String itemId;
        private final String content;

        public static Ref from(ProfileItem item) {
            return new Ref("profile", item.getId().toString(), item.getContent());
        }

        public static Ref from(SessionItem item) {
            return new Ref("session", item.getId().toString(), item.getContent());
        }
    }
}
