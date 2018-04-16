package org.mskcc.kickoff.domain;

import org.apache.commons.lang3.StringUtils;
import org.mskcc.domain.SampleSet;
import org.mskcc.kickoff.converter.SampleSetProjectInfoConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KickoffSampleSet extends SampleSet {
    private Map<String, KickoffRequest> requestIdToKickoffRequest = new LinkedHashMap<>();
    private List<KickoffExternalSample> externalSamples = new ArrayList<>();

    public KickoffSampleSet(String name) {
        super(name);
    }

    public Map<String, KickoffRequest> getRequestIdToKickoffRequest() {
        return requestIdToKickoffRequest;
    }

    public void setRequestIdToKickoffRequest(Map<String, KickoffRequest> requestIdToKickoffRequest) {
        this.requestIdToKickoffRequest = requestIdToKickoffRequest;
    }

    public List<KickoffRequest> getKickoffRequests() {
        return new ArrayList<>(requestIdToKickoffRequest.values());
    }

    public void setKickoffRequests(List<KickoffRequest> kickoffRequests) {
        for (KickoffRequest kickoffRequest : kickoffRequests) {
            requestIdToKickoffRequest.put(kickoffRequest.getId(), kickoffRequest);
        }
    }

    @Override
    public KickoffRequest getPrimaryRequest() {
        if (StringUtils.isEmpty(getPrimaryRequestId()))
            throw new SampleSetProjectInfoConverter.PrimaryRequestNotSetException(String.format("Primary request not " +
                            "set for project: %s",
                    getName()));
        if (!requestIdToKickoffRequest.containsKey(getPrimaryRequestId()))
            throw new SampleSet.PrimaryRequestNotPartOfSampleSetException(String.format("Prime request provided %s is not part of sample set %s",
                    getPrimaryRequestId(), getName()));

        return requestIdToKickoffRequest.get(getPrimaryRequestId());
    }

    public List<KickoffExternalSample> getExternalSamples() {
        return externalSamples;
    }

    public void setExternalSamples(List<KickoffExternalSample> externalSamples) {
        this.externalSamples = externalSamples;
    }
}
