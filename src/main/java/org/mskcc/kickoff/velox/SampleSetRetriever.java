package org.mskcc.kickoff.velox;

import org.mskcc.domain.Recipe;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.SampleSet;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class SampleSetRetriever {
    private final SampleSetProxy sampleSetProxy;
    private final SamplesToRequestsConverter samplesToRequestsConverter;

    public SampleSetRetriever(SampleSetProxy sampleSetProxy, SamplesToRequestsConverter samplesToRequestsConverter) {
        this.sampleSetProxy = sampleSetProxy;
        this.samplesToRequestsConverter = samplesToRequestsConverter;
    }

    public SampleSet retrieve(String projectId, ProcessingType processingType) {
        try {
            SampleSet sampleSet = new SampleSet(projectId);

            sampleSet.setRequests(getRequests(processingType));
            sampleSet.setPrimaryRequestId(sampleSetProxy.getPrimaryRequestId());
            sampleSet.setBaitSet(sampleSetProxy.getBaitVersion());
            sampleSet.setRecipe(getRecipe());

            return sampleSet;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to retrieve Sample Set: %s", projectId), e);
        }
    }

    private Recipe getRecipe() throws Exception {
        return Recipe.getRecipeByValue(sampleSetProxy.getRecipe());
    }

    private List<KickoffRequest> getRequests(ProcessingType processingType) throws Exception {
        List<KickoffRequest> kickoffRequests = new ArrayList<>();
        kickoffRequests.addAll(sampleSetProxy.getRequests(processingType));
        kickoffRequests.addAll(convertToRequests(sampleSetProxy.getSamples(), processingType));

        return kickoffRequests;
    }

    private Collection<KickoffRequest> convertToRequests(Collection<Sample> samples, ProcessingType processingType)
            throws Exception {
        return samplesToRequestsConverter.convert(samples, processingType).values();
    }
}
