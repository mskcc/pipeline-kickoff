package org.mskcc.kickoff.bic.domain;

import org.mskcc.kickoff.bic.domain.sample.Sample;

import java.util.LinkedHashSet;
import java.util.Set;

public class Patient {
    private static int count = 0;
    private Set<Sample> samples = new LinkedHashSet<>();
    private String patientId;
    private int groupNumber;

    public Patient(String patientId) {
        this.patientId = patientId;
        groupNumber = count++;
    }

    public Set<Sample> getSamples() {
        return samples;
    }

    public String getPatientId() {
        return patientId;
    }

    public int getGroupNumber() {
        return groupNumber;
    }

    public boolean isValid() {
        return !patientId.startsWith("#");
    }

    public void addSample(Sample sample) {
        samples.add(sample);
    }
}
