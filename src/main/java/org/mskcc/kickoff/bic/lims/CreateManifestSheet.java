package org.mskcc.kickoff.bic.lims;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.bic.config.AppConfiguration;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.bic.generator.ManifestGenerator;
import org.mskcc.kickoff.bic.util.Constants;
import org.mskcc.kickoff.bic.util.Utils;
import org.mskcc.kickoff.config.SpringProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;
import java.util.Objects;

import static org.mskcc.kickoff.config.Arguments.*;

/**
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 */
@ComponentScan(basePackages = "org.mskcc.kickoff.bic")
public class CreateManifestSheet {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private ManifestGenerator manifestGenerator;

    @Value("${draftProjectFilePath}")
    private String draftProjectFilePath;

    public static void main(String[] args) {
        try {
            parseArguments(args);
            //devLogger.info(String.format("Received program arguments: %s", toPrintable()));

            addShutdownHook();
            ConfigurableApplicationContext context = configureSpringContext();
            CreateManifestSheet createManifestSheet = context.getBean(CreateManifestSheet.class);

            createManifestSheet.generate(project);
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
            context.getEnvironment().setActiveProfiles(SpringProfile.PROD, SpringProfile.IGO);

        initLogger(context);

        context.register(AppConfiguration.class);
        context.register(CreateManifestSheet.class);
        context.registerShutdownHook();
        context.refresh();

        return context;
    }

    private static String[] getActiveProfiles(AnnotationConfigApplicationContext context) {
        return context.getEnvironment().getActiveProfiles();
    }

    private static boolean hasActiveProfiles(AnnotationConfigApplicationContext context) {
        return context.getEnvironment().getActiveProfiles().length > 0;
    }

    private void generate(String projectId) {
        outdir = getProjectOutputDir(projectId);
        manifestGenerator.generate(projectId);
    }

    private String getProjectOutputDir(String requestID) {
        String projectFilePath = String.format("%s/%s", draftProjectFilePath, Utils.getFullProjectNameWithPrefix(requestID));
        if (!StringUtils.isEmpty(outdir)) {
            File f = new File(outdir);
            if (f.exists() && f.isDirectory())
                return String.format("%s/%s", outdir, Utils.getFullProjectNameWithPrefix(requestID));
        }

        new File(projectFilePath).mkdirs();

        return projectFilePath;
    }

    private static void initLogger(AnnotationConfigApplicationContext context) {
        String[] activeProfiles = getActiveProfiles(context);

        String log4jPropertiesFileName = "log4j-bic-dev.properties";
        if (!isActive(activeProfiles, SpringProfile.DEV)) {
            log4jPropertiesFileName = "log4j-bic.properties";
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

    public static class MySafeShutdown extends Thread {
        @Override
        public void run() {
        }
    }
}


