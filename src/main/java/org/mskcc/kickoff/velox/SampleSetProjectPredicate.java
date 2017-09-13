package org.mskcc.kickoff.velox;

import org.apache.commons.lang3.StringUtils;
import org.mskcc.kickoff.util.Constants;

import java.util.function.Predicate;

public class SampleSetProjectPredicate implements Predicate<String> {
    @Override
    public boolean test(String projectId) {
        return StringUtils.startsWithIgnoreCase(projectId, Constants.SAMPLE_SET_PREFIX);
    }
}
