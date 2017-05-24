package org.mskcc.kickoff.config;

import com.sampullara.cli.Argument;

import java.util.Arrays;

public class Arguments {
    @Argument(alias = "k", description = "Krista's argument. For her testing")
    public static Boolean krista = false;
    @Argument(alias = "prod", description = "Production project files (goes in specific path) default to draft directory")
    public static Boolean prod = false;
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
    @Argument(alias = "options", description = "Pipeline files output dir")
    public static String[] pipeline_options;
    @Argument(alias = "t", description = "Testing projects")
    private static Boolean test = false;

    public static String toPrintable() {
        return "Arguments {" +
                "\nproject=" + project +
                ",\nkrista=" + krista +
                ",\nprod=" + prod +
                ",\nnoPortal=" + noPortal +
                ",\nforced=" + forced +
                ",\nrunAsExome=" + runAsExome +
                ",\nshiny=" + shiny +
                ",\noutdir=" + outdir +
                ",\nrerunReason=" + rerunReason +
                ",\npipeline_options=" + Arrays.toString(pipeline_options) +
                ",\ntest=" + test +
                "\n}";
    }
}
