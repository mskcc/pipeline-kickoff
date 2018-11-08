package org.mskcc.kickoff.validator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mskcc.kickoff.config.AppConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles({"test", "tango", "hold"})
@ContextConfiguration(classes = {AppConfiguration.class, RequestValidatorTest.Config.class})
public class RequestValidatorTest {
    @Autowired
    private RequestValidator requestValidator;

    @Test
    public void whenSpringStarts_shouldInjectRequestValidators() throws Exception {
        assertThat(requestValidator.getValidators().size(), is(11));
    }

    @Configuration
    @ComponentScan(
            basePackages = "org.mskcc.kickoff",
            excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class)
    )
    static class Config {
    }

}