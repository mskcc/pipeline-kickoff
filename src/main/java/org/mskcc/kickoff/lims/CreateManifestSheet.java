package org.mskcc.kickoff.lims;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.AppConfiguration;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.ManifestGenerator;
import org.mskcc.kickoff.generator.OutputDirRetriever;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private LogConfigurator logConfigurator;

    @Autowired
    private OutputDirRetriever outputDirRetriever;

    @Autowired
    private RequestProxy requestProxy;

    @Autowired
    private ProjectNameValidator projectNameValidator;

    @Value("${draftProjectFilePath}")
    private String draftProjectFilePath;

    public static void main(String[] args) {
        try {
            parseArguments(args);
            ConfigurableApplicationContext context = configureSpringContext();
            CreateManifestSheet createManifestSheet = context.getBean(CreateManifestSheet.class);
            createManifestSheet.generate(Arguments.project);
        } catch (Exception e) {
            DEV_LOGGER.error(String.format("Error while generating manifest files for project: %s", Arguments.project), e);
        }
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

    private void generate(String projectId) throws Exception {
        String projectFilePath = configure(projectId);

        KickoffRequest kickoffRequest = requestProxy.getRequest(projectId);
        kickoffRequest.setOutputPath(projectFilePath);
        manifestGenerator.generate(kickoffRequest);
    }

    private String configure(String projectId) {
        logConfigurator.configureDevLog();
        DEV_LOGGER.info(String.format("Received program arguments: %s", toPrintable()));

        String projectFilePath = outputDirRetriever.retrieve(projectId, Arguments.outdir);
        validateProjectName(projectId);
        logConfigurator.configureProjectLog(projectFilePath);

        return projectFilePath;
    }

    private void validateProjectName(String projectId) {
        projectNameValidator.validate(projectId);
    }
}


