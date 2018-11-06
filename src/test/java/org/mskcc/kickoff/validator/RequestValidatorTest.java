package org.mskcc.kickoff.validator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RequestValidatorTest.ContextConfiguration.class)
public class RequestValidatorTest {
    @Autowired
    private RequestValidator requestValidator;

    @Test
    public void when_should() throws Exception {
        assertThat(requestValidator.getValidators().size(), is(9));
    }

    @Configuration
    @ComponentScan(basePackages = "org.mskcc.kickoff.validator")
    static class ContextConfiguration {
    }
}