package org.mskcc.kickoff.bic.characterisationTest;

import org.mskcc.kickoff.bic.config.AppConfiguration;
import org.mskcc.kickoff.logger.LogConfigurator;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;

@ActiveProfiles({"test", "tango"})
@PropertySource("classpath:integrationtest.properties")
@Profile("test")
@Configuration
public class RegressionTestConfig extends AppConfiguration {
    @Bean
    public LogConfigurator logConfigurator() {
        return mock(LogConfigurator.class);
    }
}
