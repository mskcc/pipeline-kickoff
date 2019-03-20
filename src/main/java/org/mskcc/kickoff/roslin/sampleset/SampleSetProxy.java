package org.mskcc.kickoff.roslin.sampleset;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.roslin.domain.KickoffExternalSample;
import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.roslin.process.ProcessingType;

import java.util.Collection;
import java.util.List;

public interface SampleSetProxy {
    String getRecipe() throws Exception;

    String getBaitVersion() throws Exception;

    String getPrimaryRequestId() throws Exception;

    Collection<KickoffRequest> getRequests(ProcessingType processingType) throws Exception;

    Collection<Sample> getIgoSamples() throws Exception;

    List<KickoffExternalSample> getExternalSamples() throws Exception;
}
