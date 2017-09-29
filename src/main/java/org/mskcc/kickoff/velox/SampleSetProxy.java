package org.mskcc.kickoff.velox;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.Collection;

public interface SampleSetProxy {
    String getRecipe() throws Exception;

    String getBaitVersion() throws Exception;

    String getPrimaryRequestId() throws Exception;

    Collection<KickoffRequest> getRequests(ProcessingType processingType) throws Exception;

    Collection<Sample> getSamples() throws Exception;
}
