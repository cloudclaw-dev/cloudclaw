package run.cloudclaw.llm.repository;

import run.cloudclaw.llm.model.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, String> {
    Optional<LlmProvider> findByName(String name);
}
