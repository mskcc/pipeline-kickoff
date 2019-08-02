package org.mskcc.kickoff.lims;

import org.mskcc.kickoff.util.Constants;
import org.springframework.stereotype.Component;

import java.util.function.BiPredicate;

@Component
public class PooledNormalPredicate implements BiPredicate<String, String> {

    @Override
    public boolean test(String sampleId, String otherSampleId) {
        return testId(sampleId) || testId(otherSampleId);
    }

    private boolean testId(String sampleId) {
        return sampleId.startsWith("CTRL-")
                || sampleId.startsWith(Constants.FFPEPOOLEDNORMAL + "-")
                || sampleId.startsWith(Constants.FROZENPOOLEDNORMAL + "-")
                || sampleId.startsWith(Constants.MOUSEPOOLEDNORMAL + "-");
    }
}
