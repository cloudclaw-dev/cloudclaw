package run.cloudclaw.common.repository;

import run.cloudclaw.common.model.ProfileItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileItemRepository extends JpaRepository<ProfileItem, String> {
    List<ProfileItem> findByUserIdOrderByCreatedAtAsc(String userId);
    void deleteByUserIdAndId(String userId, String id);
}
