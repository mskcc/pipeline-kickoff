package org.mskcc.kickoff.bic.domain.sample;

import org.mskcc.kickoff.bic.domain.Run;
import org.mskcc.kickoff.bic.util.Constants;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PooledNormalSample extends Sample {
    public PooledNormalSample(String id) {
        super(id);
    }

    @Override
    public boolean isValid() {
        return !getProperties().containsKey(Constants.CAPTURE_BAIT_SET) || !getProperties().get(Constants.CAPTURE_BAIT_SET).contains(",");
    }

    @Override
    public Set<Run> getValidRuns() {
        return getRuns().values().stream()
                .filter(r -> Run.getAllRuns(Run::isPassed).contains(r) || r.isValid()).collect(Collectors.toSet());
    }

    @Override
    public Set<String> getValidRunIds() {
        Set<String> includeRunIds = Arrays.stream(get(Constants.INCLUDE_RUN_ID).split(";")).filter(r -> !"".equals(r)).collect(Collectors.toSet());
        return includeRunIds;
    }

    @Override
    public Run putRunIfAbsent(String runID) {
        Map<String, Run> runs = getRuns();
        if (!runs.containsKey(runID)) {
            Run run = new Run(runID);
            runs.put(runID, run);
        }
        return runs.get(runID);
    }
}
