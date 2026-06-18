package run.cloudclaw.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelConfigDTO {
    private String id;
    private String channelType;
    private String name;
    private Boolean enabled;
    private String appId;
    private String appSecret;     // write: plain text; read: "******"
    private String verificationToken;
    private String encryptKey;
    private String redirectUri;
    private String extraConfig;
    private String agentId;
    private String connectionMode;
    private String connectionStatus;
    private LocalDateTime lastConnectedAt;
    private String purpose;
    private String webhookUrl;    // read-only: auto-generated event callback URL
}
