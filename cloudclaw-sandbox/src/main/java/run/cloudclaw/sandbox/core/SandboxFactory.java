package run.cloudclaw.sandbox.core;

import run.cloudclaw.sandbox.model.SandboxProvider;
import org.springaicommunity.sandbox.LocalSandbox;
import org.springaicommunity.sandbox.Sandbox;
import org.springaicommunity.sandbox.docker.DockerSandbox;
import org.springaicommunity.sandbox.e2b.E2BSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Factory for creating Sandbox instances based on SandboxProvider config.
 */
public class SandboxFactory {

    private static final Logger log = LoggerFactory.getLogger(SandboxFactory.class);

    public static Sandbox create(SandboxProvider provider, String prefix) {
        if (provider == null) {
            throw new IllegalArgumentException("Sandbox provider is required when backend is not explicitly configured");
        }
        return switch (provider.getType()) {
            case "DOCKER" -> createDocker(provider);
            case "E2B" -> createE2B(provider);
            case "LOCAL" -> createLocal(provider, prefix);
            default -> throw new IllegalArgumentException("Unsupported sandbox provider type: " + provider.getType());
        };
    }

    /** Legacy support: create from backend type string */
    public static Sandbox create(String backend, String prefix) {
        if (backend == null || backend.isBlank()) {
            throw new IllegalArgumentException("Sandbox backend is required");
        }
        return switch (backend) {
            case "DOCKER" -> DockerSandbox.builder().image("python:3.11-slim").build();
            case "E2B" -> E2BSandbox.builder().build();
            case "LOCAL" -> LocalSandbox.builder().tempDirectory(prefix).build();
            default -> throw new IllegalArgumentException("Unsupported sandbox backend: " + backend);
        };
    }

    private static Sandbox createLocal(String prefix) {
        log.debug("Creating LocalSandbox with prefix={}", prefix);
        return LocalSandbox.builder().tempDirectory(prefix).build();
    }

    private static Sandbox createLocal(SandboxProvider provider, String prefix) {
        String workDir = provider.getLocalWorkDirBase() != null ? provider.getLocalWorkDirBase() : prefix;
        log.debug("Creating LocalSandbox with prefix={}", workDir);
        return LocalSandbox.builder().tempDirectory(workDir).build();
    }

    private static Sandbox createDocker(SandboxProvider provider) {
        log.info("Creating DockerSandbox (name={})", provider.getName());
        String image = "python:3.11-slim"; // default
        if (provider.getDockerImages() != null && !provider.getDockerImages().isBlank()) {
            // dockerImages is JSON like {"python":"python:3.11-slim"}
            // Use python image as default for now
            image = extractDefaultImage(provider.getDockerImages());
        }
        return DockerSandbox.builder()
                .image(image)
                .build();
    }

    private static Sandbox createE2B(SandboxProvider provider) {
        log.info("Creating E2BSandbox (name={}, template={})", provider.getName(), provider.getE2bTemplateId());
        E2BSandbox.Builder builder = E2BSandbox.builder();
        if (provider.getE2bApiKey() != null) builder.apiKey(provider.getE2bApiKey());
        if (provider.getE2bTemplateId() != null) builder.template(provider.getE2bTemplateId());
        if (provider.getE2bApiUrl() != null) builder.apiUrl(provider.getE2bApiUrl());
        return builder.build();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    /** Extract first image from JSON like {"python":"python:3.11-slim","javascript":"node:20-slim"} */
    private static String extractDefaultImage(String imagesJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode node = OBJECT_MAPPER.readTree(imagesJson.trim());
            if (node.isObject()) {
                var field = node.fields();
                if (field.hasNext()) {
                    return field.next().getValue().asText();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse docker images JSON: {}", imagesJson);
        }
        return "python:3.11-slim";
    }
}
