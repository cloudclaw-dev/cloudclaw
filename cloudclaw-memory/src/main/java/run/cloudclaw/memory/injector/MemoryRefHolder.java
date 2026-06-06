package run.cloudclaw.memory.injector;

import run.cloudclaw.common.model.ProfileItem;
import run.cloudclaw.common.model.SessionItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-local holder for memory references collected during prompt assembly.
 * This allows ChatEngine to retrieve the refs after the prompt is built.
 */
public class MemoryRefHolder {

    private static final ThreadLocal<List<MemoryContext.Ref>> HOLDER = new ThreadLocal<>();

    public static void set(List<MemoryContext.Ref> refs) {
        HOLDER.set(refs);
    }

    public static List<MemoryContext.Ref> get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * Set refs from a MemoryContext and return the content string.
     */
    public static String captureAndReturnContent(MemoryContext ctx) {
        if (ctx == null) {
            HOLDER.set(new ArrayList<>());
            return "";
        }
        List<MemoryContext.Ref> refs = new ArrayList<>();
        if (ctx.getIncludedProfileItems() != null) {
            for (ProfileItem item : ctx.getIncludedProfileItems()) {
                refs.add(MemoryContext.Ref.from(item));
            }
        }
        if (ctx.getIncludedSessionItems() != null) {
            for (SessionItem item : ctx.getIncludedSessionItems()) {
                refs.add(MemoryContext.Ref.from(item));
            }
        }
        HOLDER.set(refs);
        return ctx.getContent();
    }
}
