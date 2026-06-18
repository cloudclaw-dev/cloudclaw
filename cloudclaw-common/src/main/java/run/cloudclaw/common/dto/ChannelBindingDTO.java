package run.cloudclaw.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelBindingDTO {
    private String channelType;
    private String channelUserId;
    private String channelData;   // JSON with name, avatar, etc.
    private String createdAt;
}
