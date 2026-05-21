    package run.cloudclaw.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_skills")
@IdClass(AgentSkill.AgentSkillId.class)
public class AgentSkill {

    @Id
    @Column(name = "agent_id", nullable = false, columnDefinition = "uuid")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID agentId;

    @Id
    @Column(name = "skill_id", nullable = false, columnDefinition = "uuid")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID skillId;

    public static class AgentSkillId implements Serializable {
        private UUID agentId;
        private UUID skillId;

        public AgentSkillId() {
        }

        public AgentSkillId(UUID agentId, UUID skillId) {
            this.agentId = agentId;
            this.skillId = skillId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgentSkillId that = (AgentSkillId) o;
            return Objects.equals(agentId, that.agentId) && Objects.equals(skillId, that.skillId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentId, skillId);
        }
    }
}
