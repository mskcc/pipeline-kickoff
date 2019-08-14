package org.mskcc.kickoff.lims;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.AppConfiguration;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.generator.ManifestGenerator;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.velox.util.VeloxUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;

import static org.mskcc.kickoff.config.Arguments.outdir;
import static org.mskcc.kickoff.config.Arguments.parseArguments;

/**
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 */
@ComponentScan(basePackages = "org.mskcc.kickoff")
class CreateManifestSheet {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private ManifestGenerator manifestGenerator;

    @Value("${draftProjectFilePath}")
    private String draftProjectFilePath;

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
        outdir = getProjectOutputDir(Arguments.project);
        manifestGenerator.generate();
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

    public static class MySafeShutdown extends Thread {
        @Override
        public void run() {
            VeloxUtils.closeConnection();
        }
    }
}


