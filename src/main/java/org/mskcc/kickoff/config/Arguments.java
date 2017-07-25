package org.mskcc.kickoff.config;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.mskcc.kickoff.util.Constants;

public class Arguments {
    private static final org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(Constants.DEV_LOGGER);

    @Argument(alias = "k", description = "Krista's argument. For her testing")
    public static Boolean krista = false;
    @Argument(alias = "noPortal", description = "This is suppress creation of portal config file.")
    public static Boolean noPortal = false;
    @Argument(alias = "f", description = "Force pulling all samples even if they don't have QC passed.")
    public static Boolean forced = false;
    @Argument(alias = "exome", description = "Run exome project even IF project pulls as an impact")
    public static Boolean runAsExome = false;
    @Argument(alias = "s", description = "Shiny user is running this script (rnaseq projects will die).")
    public static Boolean shiny = false;
    @Argument(alias = "p", description = "Project to get samples for", required = true)
    public static String project;
    @Argument(alias = "o", description = "Pipeline files output dir")
    public static String outdir;
    @Argument(alias = "rerunReason", description = "Reason for rerun, *REQUIRED if this is not the first run for this project*")
    public static String rerunReason;

    public static String toPrintable() {
        return "Arguments {" +
                "\nproject=" + project +
                ",\nkrista=" + krista +
                ",\nnoPortal=" + noPortal +
                ",\nforced=" + forced +
                ",\nrunAsExome=" + runAsExome +
                ",\nshiny=" + shiny +
                ",\noutdir=" + outdir +
                ",\nrerunReason=" + rerunReason +
                "\n}";
    }

    public static void parseArguments(String[] args) {
        try {
            Args.parse(Arguments.class, args);
        } catch (Exception e) {
            LOGGER.error("Wrong arguments provided", e);
            Args.usage(Arguments.class);
        }
    }
}
