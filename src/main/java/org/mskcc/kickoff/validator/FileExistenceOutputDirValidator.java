package org.mskcc.kickoff.validator;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.function.Predicate;

@Component
public class FileExistenceOutputDirValidator implements Predicate<String> {
    @Override
    public boolean test(String outputDir) {
        return !StringUtils.isEmpty(outputDir)
                && new File(outputDir).exists()
                && new File(outputDir).isDirectory();
    }
}
