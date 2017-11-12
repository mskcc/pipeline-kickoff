package org.mskcc.kickoff.lims;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.AppConfiguration;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.generator.ManifestGenerator;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import static org.mskcc.kickoff.config.Arguments.parseArguments;
import static org.mskcc.kickoff.config.Arguments.toPrintable;

/**
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 */
@ComponentScan(basePackages = "org.mskcc.kickoff")
class CreateManifestSheet {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private ManifestGenerator manifestGenerator;

    public static void main(String[] args) {
        try {
            parseArguments(args);
            DEV_LOGGER.info(String.format("Received program arguments: %s", toPrintable()));

            ConfigurableApplicationContext context = configureSpringContext();
            CreateManifestSheet createManifestSheet = context.getBean(CreateManifestSheet.class);
            createManifestSheet.generate(Arguments.project);
        } catch (Exception e) {
            DEV_LOGGER.error(String.format("Error while generating manifest files for project: %s", Arguments.project), e);
        }
    }

    private static ConfigurableApplicationContext configureSpringContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        configureSpringProfiles(context);
        context.register(AppConfiguration.class);
        context.register(CreateManifestSheet.class);
        context.registerShutdownHook();
        context.refresh();

        return context;
    }

    private static void configureSpringProfiles(AnnotationConfigApplicationContext context) {
        if (getActiveProfiles(context).length == 0) {
            DEV_LOGGER.info(String.format("No spring profiles set. Setting default spring profiles: %s, %s", Constants.PROD_PROFILE, Constants.IGO_PROFILE));
            context.getEnvironment().setActiveProfiles(Constants.PROD_PROFILE, Constants.IGO_PROFILE);
        } else {
            DEV_LOGGER.info(String.format("Spring profiles set: %s", StringUtils.join(getActiveProfiles(context), ",")));
        }
    }

    private static String[] getActiveProfiles(AnnotationConfigApplicationContext context) {
        return context.getEnvironment().getActiveProfiles();
    }

    private void generate(String projectId) throws Exception {
        manifestGenerator.generate(projectId);
    }
}


