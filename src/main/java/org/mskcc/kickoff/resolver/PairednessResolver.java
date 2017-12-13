package org.mskcc.kickoff.resolver;

import org.mskcc.domain.Pairedness;
import org.mskcc.util.Constants;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
public class PairednessResolver {
    public Pairedness resolve(String path) throws IOException {
        if (isPaired(path))
            return Pairedness.PE;
        return Pairedness.SE;
    }

    private boolean isPaired(String path) throws IOException {
        return Files.list(Paths.get(path))
                .anyMatch(f -> f.getFileName().toString().endsWith(Constants.PAIRED_FILE_SUFFIX));
    }
}
