package run.cloudclaw.sandbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sandbox_providers")
public class SandboxProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // LOCAL, DOCKER, E2B

    @Column(nullable = false)
    private Boolean enabled = true;

    /** Docker config: JSON mapping language->image, e.g. {"python":"python:3.11-slim","javascript":"node:20-slim"} */
    @Column(columnDefinition = "TEXT")
    private String dockerImages;

    @Column
    private String dockerMemory;

    @Column
    private Integer dockerCpus;

    @Column
    private Boolean dockerNetworkEnabled;

    /** E2B config */
    @Column
    private String e2bApiKey;

    @Column
    private String e2bTemplateId;

    @Column
    private String e2bApiUrl;

    /** Local config */
    @Column
    private String localWorkDirBase;

    @Column(nullable = false)
    private Integer defaultTimeout = 30;

    @Column(nullable = false)
    private Integer maxTimeout = 300;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    public SandboxProvider() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getDockerImages() { return dockerImages; }
    public void setDockerImages(String dockerImages) { this.dockerImages = dockerImages; }
    public String getDockerMemory() { return dockerMemory; }
    public void setDockerMemory(String dockerMemory) { this.dockerMemory = dockerMemory; }
    public Integer getDockerCpus() { return dockerCpus; }
    public void setDockerCpus(Integer dockerCpus) { this.dockerCpus = dockerCpus; }
    public Boolean getDockerNetworkEnabled() { return dockerNetworkEnabled; }
    public void setDockerNetworkEnabled(Boolean dockerNetworkEnabled) { this.dockerNetworkEnabled = dockerNetworkEnabled; }
    public String getE2bApiKey() { return e2bApiKey; }
    public void setE2bApiKey(String e2bApiKey) { this.e2bApiKey = e2bApiKey; }
    public String getE2bTemplateId() { return e2bTemplateId; }
    public void setE2bTemplateId(String e2bTemplateId) { this.e2bTemplateId = e2bTemplateId; }
    public String getE2bApiUrl() { return e2bApiUrl; }
    public void setE2bApiUrl(String e2bApiUrl) { this.e2bApiUrl = e2bApiUrl; }
    public String getLocalWorkDirBase() { return localWorkDirBase; }
    public void setLocalWorkDirBase(String localWorkDirBase) { this.localWorkDirBase = localWorkDirBase; }
    public Integer getDefaultTimeout() { return defaultTimeout; }
    public void setDefaultTimeout(Integer defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    public Integer getMaxTimeout() { return maxTimeout; }
    public void setMaxTimeout(Integer maxTimeout) { this.maxTimeout = maxTimeout; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
