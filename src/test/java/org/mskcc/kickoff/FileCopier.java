package org.mskcc.kickoff;

import jdk.nashorn.internal.ir.annotations.Immutable;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Immutable
class FileCopier {
    private static final Logger LOGGER = Logger.getLogger(FileCopier.class);
    public static void copy(Path source, Path dest) {
        Path failingDestination = Paths.get(String.format("%s/%s", dest, source.getFileName()));
        if (!failingDestination.toFile().exists())
            try {
                Files.copy(source, failingDestination);
            } catch (IOException e) {
                LOGGER.warn(String.format("Copying file: %s to: %s failed", source, failingDestination), e);
            }
    }
}
