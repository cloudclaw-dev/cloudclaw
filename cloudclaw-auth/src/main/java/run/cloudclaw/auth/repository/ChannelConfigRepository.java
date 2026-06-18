package run.cloudclaw.auth.repository;

import run.cloudclaw.common.model.ChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelConfigRepository extends JpaRepository<ChannelConfig, String> {

    Optional<ChannelConfig> findByChannelType(String channelType);

    List<ChannelConfig> findAllByChannelTypeAndEnabled(String channelType, Boolean enabled);

    // Find by channel type, enabled, and purpose (for login vs bot selection)
    List<ChannelConfig> findByChannelTypeAndEnabledAndPurposeIn(
            String channelType, Boolean enabled, java.util.List<String> purposes);

    Optional<ChannelConfig> findByChannelTypeAndAppId(String channelType, String appId);

    List<ChannelConfig> findAllByAgentId(String agentId);
}
