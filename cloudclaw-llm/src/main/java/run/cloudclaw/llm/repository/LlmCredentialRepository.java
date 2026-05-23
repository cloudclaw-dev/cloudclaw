package run.cloudclaw.llm.repository;

import run.cloudclaw.llm.model.LlmCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmCredentialRepository extends JpaRepository<LlmCredential, String> {
    List<LlmCredential> findByProviderIdAndEnabledTrueOrderByPriorityAsc(String providerId);
    List<LlmCredential> findByProviderId(String providerId);
}
