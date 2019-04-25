package org.mskcc.kickoff.poolednormals;

import org.mskcc.kickoff.util.Constants;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class PooledNormalPredicate implements Predicate<String> {

    @Override
    public boolean test(String sampleId) {
        return sampleId.startsWith("CTRL-")
                || sampleId.startsWith(Constants.FFPEPOOLEDNORMAL + "-")
                || sampleId.startsWith(Constants.FROZENPOOLEDNORMAL + "-")
                || sampleId.startsWith(Constants.MOUSEPOOLEDNORMAL + "-");
    }
}
