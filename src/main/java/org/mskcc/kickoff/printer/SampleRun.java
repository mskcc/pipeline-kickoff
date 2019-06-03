package org.mskcc.kickoff.printer;

import org.mskcc.domain.sample.Sample;

public class SampleRun {
    private final Sample sample;
    private final String runId;

    public SampleRun(Sample sample, String runId) {
        this.sample = sample;
        this.runId = runId;
    }

    public Sample getSample() {
        return sample;
    }

    public String getRunId() {
        return runId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SampleRun sampleRun = (SampleRun) o;

        return sample.getCmoSampleId().equals(sampleRun.sample.getCmoSampleId()) && runId.equals(sampleRun.runId);
    }

    @Override
    public int hashCode() {
        int result = sample.getCmoSampleId().hashCode();
        result = 31 * result + runId.hashCode();
        return result;
    }
}
