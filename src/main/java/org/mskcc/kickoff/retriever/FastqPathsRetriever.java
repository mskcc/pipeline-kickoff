package org.mskcc.kickoff.retriever;

import org.mskcc.domain.Request;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public interface FastqPathsRetriever {
    List<String> retrieve(KickoffRequest request, Sample sample, String runIDFull, String samplePattern)
            throws IOException, InterruptedException;

    Optional<String> getRunId(KickoffRequest request, HashSet<String> runsWithMultipleFolders, KickoffRequest
            singleRequest, String runId);

    void validateSampleSheetExist(String path, Request request, Object sample, Object runIDFull);
}
