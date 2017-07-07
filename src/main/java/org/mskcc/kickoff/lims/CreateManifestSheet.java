package org.mskcc.kickoff.lims;

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

/**
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 */
@ComponentScan(basePackages = "org.mskcc.kickoff")
class CreateManifestSheet {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private ManifestGenerator manifestGenerator;

    public static void main(String[] args) {
        try {
            parseArguments(args);
            addShutdownHook();
            ConfigurableApplicationContext context = configureSpringContext();
            CreateManifestSheet createManifestSheet = context.getBean(CreateManifestSheet.class);

            createManifestSheet.generate();
        } catch (Exception e) {
            devLogger.error(String.format("Error while generating manifest files for project: %s", Arguments.project), e);
        }
    }

    private static void addShutdownHook() {
        MySafeShutdown sh = new MySafeShutdown();
        Runtime.getRuntime().addShutdownHook(sh);
    }

    private static ConfigurableApplicationContext configureSpringContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!hasActiveProfiles(context))
            context.getEnvironment().setActiveProfiles(Constants.PROD_PROFILE, Constants.IGO_PROFILE);

        context.register(AppConfiguration.class);
        context.register(CreateManifestSheet.class);
        context.registerShutdownHook();
        context.refresh();

        return context;
    }

    private static boolean hasActiveProfiles(AnnotationConfigApplicationContext context) {
        return context.getEnvironment().getActiveProfiles().length > 0;
    }

    private void generate() {
        manifestGenerator.generate();
    }

    public static class MySafeShutdown extends Thread {
        @Override
        public void run() {
        }
    }
}


