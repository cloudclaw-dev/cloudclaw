package run.cloudclaw.auth.repository;

import run.cloudclaw.common.model.ChannelBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelBindingRepository extends JpaRepository<ChannelBinding, String> {

    Optional<ChannelBinding> findByChannelTypeAndChannelUserId(String channelType, String channelUserId);

    Optional<ChannelBinding> findByUserIdAndChannelType(String userId, String channelType);

    List<ChannelBinding> findByUserId(String userId);

    void deleteByUserIdAndChannelType(String userId, String channelType);
}
