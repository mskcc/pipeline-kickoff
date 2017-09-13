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
    private SampleSet sampleSet;
    private List<KickoffRequest> kickoffRequests;

    public SampleSetRetriever(SampleSetProxy sampleSetProxy, SamplesToRequestsConverter samplesToRequestsConverter) {
        this.sampleSetProxy = sampleSetProxy;
        this.samplesToRequestsConverter = samplesToRequestsConverter;
    }

    public SampleSet retrieve(String projectId, ProcessingType processingType) throws Exception {
        sampleSet = new SampleSet(projectId);
        kickoffRequests = new ArrayList<>();

        kickoffRequests.addAll(sampleSetProxy.getRequests(processingType));
        kickoffRequests.addAll(convertToRequests(sampleSetProxy.getSamples(), processingType));
        sampleSet.setRequests(kickoffRequests);
        sampleSet.setPrimaryRequestId(sampleSetProxy.getPrimaryRequestId());
        sampleSet.setBaitSet(sampleSetProxy.getBaitVersion());
        String recipe = sampleSetProxy.getRecipe();
        sampleSet.setRecipe(Recipe.getRecipeByValue(recipe));

        return sampleSet;
    }

    private Collection<KickoffRequest> convertToRequests(Collection<Sample> samples, ProcessingType processingType) throws Exception {
        return samplesToRequestsConverter.convert(samples, processingType).values();
    }


}
