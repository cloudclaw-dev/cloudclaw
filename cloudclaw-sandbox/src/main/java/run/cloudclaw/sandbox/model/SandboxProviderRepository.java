package run.cloudclaw.sandbox.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SandboxProviderRepository extends JpaRepository<SandboxProvider, UUID> {
    List<SandboxProvider> findByEnabledTrue();
}
