package run.cloudclaw.llm.repository;

import run.cloudclaw.llm.model.LlmModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmModelRepository extends JpaRepository<LlmModel, String> {
    List<LlmModel> findByProviderId(String providerId);
    List<LlmModel> findByModelType(String modelType);
}
