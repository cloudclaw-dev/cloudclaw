package run.cloudclaw.skill.config;

import run.cloudclaw.skill.repository.AgentSkillRepository;
import run.cloudclaw.skill.repository.SkillFileRepository;
import run.cloudclaw.skill.repository.SkillRepository;
import run.cloudclaw.skill.service.SkillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
public class SkillAutoConfiguration {

    @Bean
    public SkillService skillService(SkillRepository skillRepository,
                                     AgentSkillRepository agentSkillRepository,
                                     SkillFileRepository skillFileRepository) {
        log.info("Initializing CloudClaw SkillService");
        return new SkillService(skillRepository, agentSkillRepository, skillFileRepository);
    }
}
