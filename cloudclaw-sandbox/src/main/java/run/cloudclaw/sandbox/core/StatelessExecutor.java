package run.cloudclaw.sandbox.core;

import run.cloudclaw.sandbox.config.SandboxProperties;
import org.springaicommunity.sandbox.ExecResult;
import org.springaicommunity.sandbox.ExecSpec;
import org.springaicommunity.sandbox.Sandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Stateless code executor. Creates a temporary sandbox, executes code, and cleans up immediately.
 */
@Component
public class StatelessExecutor {

    private static final Logger log = LoggerFactory.getLogger(StatelessExecutor.class);

    private final SandboxProperties properties;

    public StatelessExecutor(SandboxProperties properties) {
        this.properties = properties;
    }

    public String execute(SandboxBackend backend, String language, String code, Duration timeout) {
        validateLanguage(language);

        try (Sandbox sandbox = SandboxFactory.create(backend.name(), "cloudclaw-st-")) {
            String filename = languageToFilename(language);
            sandbox.files().create(filename, code);

            List<String> command = buildCommand(language, filename);
            ExecSpec spec = ExecSpec.builder()
                    .command(command)
                    .timeout(timeout)
                    .build();

            ExecResult result = sandbox.exec(spec);
            return formatResult(result);
        } catch (Exception e) {
            log.error("Stateless execution failed (backend={}): {}", backend, e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private void validateLanguage(String language) {
        if (!properties.getAllowedLanguages().contains(language.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Language '" + language + "' not allowed. Allowed: " + properties.getAllowedLanguages());
        }
    }

    // ========== Static helpers shared with SandboxManager ==========

    public static String languageToFilename(String language) {
        return switch (language.toLowerCase()) {
            case "python" -> "script.py";
            case "javascript" -> "script.js";
            case "shell" -> "script.sh";
            case "java" -> "Main.java";
            default -> "script";
        };
    }

    public static List<String> buildCommand(String language, String filename) {
        return switch (language.toLowerCase()) {
            case "python" -> List.of("python", "-u", filename);
            case "javascript" -> List.of("node", filename);
            case "shell" -> List.of("bash", filename);
            case "java" -> List.of("sh", "-c", "javac Main.java && java Main");
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }

    public static String formatResult(ExecResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<returncode>").append(result.exitCode()).append("</returncode>\n");
        if (result.hasStdout()) {
            sb.append("<stdout>").append(result.stdout()).append("</stdout>\n");
        }
        if (result.hasStderr()) {
            sb.append("<stderr>").append(result.stderr()).append("</stderr>\n");
        }
        if (result.duration() != null) {
            sb.append("<duration>").append(result.duration().toMillis()).append("ms</duration>");
        }
        return sb.toString();
    }
}
