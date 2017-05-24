package org.mskcc.kickoff.config;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class ArgumentsFileReporter {
    private static final Logger LOGGER = Logger.getLogger(ArgumentsFileReporter.class);

    public void printCurrentArgumentsToFile() {
        File file;
        String path = Utils.getRunInfoPath(Paths.get(Arguments.outdir));
        try {
            file = getFile(path);
            List<String> arguments = getArguments();
            writeToFile(file, arguments);
        } catch (IOException e) {
            LOGGER.info(String.format("Unable to write current arguments to file: %s", path));
        }
    }

    private void writeToFile(File file, List<String> content) throws IOException {
        Files.write(file.toPath(), content, Charset.forName("UTF-8"));
    }

    private List<String> getArguments() {
        String arguments = Arguments.toPrintable();
        return Arrays.asList(arguments.split("\\n"));
    }

    private File getFile(String runInfoPath) throws IOException {
        File file = new File(runInfoPath);
        if (!file.exists()) {
            (new File(Arguments.outdir)).mkdirs();
            file.createNewFile();
        }
        return file;
    }
}
