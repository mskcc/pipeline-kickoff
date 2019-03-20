package org.mskcc.kickoff.roslin.validator;

import org.apache.commons.lang3.StringUtils;
import org.mskcc.kickoff.roslin.util.Constants;

import java.util.function.Predicate;

public class SampleSetNamePredicate implements Predicate<String> {
    @Override
    public boolean test(String projectId) {
        return StringUtils.startsWithIgnoreCase(projectId, Constants.SAMPLE_SET_PREFIX);
    }
}
