package org.mskcc.kickoff.bic.domain;

import org.mskcc.kickoff.bic.domain.sample.Sample;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class Pool extends Sample {
    private Set<Sample> samples = new LinkedHashSet<>();

    public Pool(String igoId) {
        super(igoId);
    }

    public void addSample(Sample sample) {
        samples.add(sample);
    }

    public Set<Sample> getSamples() {
        return samples;
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
