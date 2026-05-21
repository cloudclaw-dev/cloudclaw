package run.cloudclaw.sandbox.config;

import run.cloudclaw.sandbox.core.SandboxBackend;
import run.cloudclaw.sandbox.core.SandboxMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "cloudclaw.sandbox")
public class SandboxProperties {

    private SandboxBackend defaultBackend = SandboxBackend.LOCAL;
    private SandboxMode defaultMode = SandboxMode.STATELESS;
    private Duration defaultTimeout = Duration.ofSeconds(30);
    private Duration maxTimeout = Duration.ofMinutes(5);
    private int maxConcurrent = 10;
    private List<String> allowedLanguages = List.of("python", "javascript", "shell", "java");

    private Local local = new Local();
    private Docker docker = new Docker();
    private E2B e2b = new E2B();

    public SandboxBackend getDefaultBackend() { return defaultBackend; }
    public void setDefaultBackend(SandboxBackend v) { this.defaultBackend = v; }
    public SandboxMode getDefaultMode() { return defaultMode; }
    public void setDefaultMode(SandboxMode v) { this.defaultMode = v; }
    public Duration getDefaultTimeout() { return defaultTimeout; }
    public void setDefaultTimeout(Duration v) { this.defaultTimeout = v; }
    public Duration getMaxTimeout() { return maxTimeout; }
    public void setMaxTimeout(Duration v) { this.maxTimeout = v; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int v) { this.maxConcurrent = v; }
    public List<String> getAllowedLanguages() { return allowedLanguages; }
    public void setAllowedLanguages(List<String> v) { this.allowedLanguages = v; }
    public Local getLocal() { return local; }
    public void setLocal(Local v) { this.local = v; }
    public Docker getDocker() { return docker; }
    public void setDocker(Docker v) { this.docker = v; }
    public E2B getE2b() { return e2b; }
    public void setE2b(E2B v) { this.e2b = v; }

    public static class Local {
        private String workDirBase;
        public String getWorkDirBase() { return workDirBase; }
        public void setWorkDirBase(String v) { this.workDirBase = v; }
    }

    public static class Docker {
        private boolean enabled = false;
        private Map<String, String> images = Map.of("python", "python:3.11-slim");
        private String memory = "512m";
        private int cpus = 1;
        private boolean networkEnabled = false;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public Map<String, String> getImages() { return images; }
        public void setImages(Map<String, String> v) { this.images = v; }
        public String getMemory() { return memory; }
        public void setMemory(String v) { this.memory = v; }
        public int getCpus() { return cpus; }
        public void setCpus(int v) { this.cpus = v; }
        public boolean isNetworkEnabled() { return networkEnabled; }
        public void setNetworkEnabled(boolean v) { this.networkEnabled = v; }
    }

    public static class E2B {
        private boolean enabled = false;
        private String apiKey;
        private String templateId;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String v) { this.templateId = v; }
    }
}
