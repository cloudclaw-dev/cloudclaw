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
@Table(name = "agent_mcp_servers")
@IdClass(AgentMcpServer.AgentMcpServerId.class)
public class AgentMcpServer {

    @Id
    @Column(name = "agent_id", nullable = false, columnDefinition = "uuid")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID agentId;

    @Id
    @Column(name = "server_id", nullable = false, columnDefinition = "uuid")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    private UUID serverId;

    public static class AgentMcpServerId implements Serializable {
        private UUID agentId;
        private UUID serverId;

        public AgentMcpServerId() {
        }

        public AgentMcpServerId(UUID agentId, UUID serverId) {
            this.agentId = agentId;
            this.serverId = serverId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AgentMcpServerId that = (AgentMcpServerId) o;
            return Objects.equals(agentId, that.agentId) && Objects.equals(serverId, that.serverId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(agentId, serverId);
        }
    }
}
