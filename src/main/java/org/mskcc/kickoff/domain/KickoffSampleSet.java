package org.mskcc.kickoff.domain;

import org.mskcc.domain.SampleSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KickoffSampleSet extends SampleSet {
    private Map<String, KickoffRequest> requestIdToKickoffRequest = new LinkedHashMap<>();

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
}
