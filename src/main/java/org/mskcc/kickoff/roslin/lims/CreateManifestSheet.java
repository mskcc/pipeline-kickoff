package org.mskcc.kickoff.roslin.lims;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.SpringProfile;
import org.mskcc.kickoff.roslin.config.AppConfiguration;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.roslin.generator.ManifestGenerator;
import org.mskcc.kickoff.roslin.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Objects;

import static org.mskcc.kickoff.config.Arguments.parseArguments;
import static org.mskcc.kickoff.config.Arguments.toPrintable;

/**
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 */
public class CreateManifestSheet {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private ManifestGenerator manifestGenerator;

    public static void main(String[] args) {
        try {
            ConfigurableApplicationContext context = configureSpringContext();
            CreateManifestSheet createManifestSheet = context.getBean(CreateManifestSheet.class);

            parseArguments(args);
            DEV_LOGGER.info(String.format("Received program arguments: %s", toPrintable()));

            createManifestSheet.generate(Arguments.project);
        } catch (Exception e) {
            DEV_LOGGER.error(String.format("Error while generating manifest files for project: %s", Arguments
                    .project), e);
        }
    }

    private static ConfigurableApplicationContext configureSpringContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

        initLogger(context);

        configureSpringProfiles(context);
        context.register(AppConfiguration.class);
        context.register(CreateManifestSheet.class);
        context.registerShutdownHook();
        context.refresh();

        return context;
    }

    private static void configureSpringProfiles(AnnotationConfigApplicationContext context) {
        String[] activeProfiles = getActiveProfiles(context);

        if (activeProfiles.length == 0) {
            DEV_LOGGER.info(String.format("No spring profiles set. Setting default spring profiles: %s, %s",
                    SpringProfile.PROD, SpringProfile.IGO));
            context.getEnvironment().setActiveProfiles(SpringProfile.PROD, SpringProfile.IGO);
        } else {
            DEV_LOGGER.info(String.format("Spring profiles set: %s", StringUtils.join(activeProfiles, "," +
                    "")));
        }
    }

    private static void initLogger(AnnotationConfigApplicationContext context) {
        String[] activeProfiles = getActiveProfiles(context);

        String log4jPropertiesFileName = "log4j-roslin-dev.properties";
        if (!isActive(activeProfiles, SpringProfile.DEV)) {
            log4jPropertiesFileName = "log4j-roslin.properties";
        }

        AppConfiguration.configureLogger(log4jPropertiesFileName);
    }

    private static boolean isActive(String[] activeProfiles, String profileName) {
        for (String activeProfile : activeProfiles) {
            if (Objects.equals(activeProfile, profileName))
                return true;
        }

        return false;
    }

    private static String[] getActiveProfiles(AnnotationConfigApplicationContext context) {
        return context.getEnvironment().getActiveProfiles();
    }

    private void generate(String projectId) throws Exception {
        manifestGenerator.generate(projectId);
    }
}


