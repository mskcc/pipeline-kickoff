package org.mskcc.kickoff.domain;

import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SampleSet {
    private final String name;
    private Map<String, KickoffRequest> requestIdToRequest = new LinkedHashMap<>();
    private List<PairingInfo> pairings = new ArrayList<>();
    private String primaryRequestId;
    private String baitSet;
    private String finalProjectTitle;
    private Recipe recipe;

    public SampleSet(String name) {
        this.name = name;
    }

    public List<KickoffRequest> getRequests() {
        return new ArrayList<>(requestIdToRequest.values());
    }

    public void setRequests(List<KickoffRequest> kickoffRequests) {
        for (KickoffRequest kickoffRequest : kickoffRequests) {
            requestIdToRequest.put(kickoffRequest.getId(), kickoffRequest);
        }
    }

    public Map<String, KickoffRequest> getRequestIdToRequest() {
        return requestIdToRequest;
    }

    public String getPrimaryRequestId() {
        return primaryRequestId;
    }

    public void setPrimaryRequestId(String primaryRequestId) {
        this.primaryRequestId = primaryRequestId;
    }

    public String getBaitSet() {
        return baitSet;
    }

    public void setBaitSet(String baitSet) {
        this.baitSet = baitSet;
    }

    public String getFinalProjectTitle() {
        return finalProjectTitle;
    }

    public void setFinalProjectTitle(String finalProjectTitle) {
        this.finalProjectTitle = finalProjectTitle;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }

    public String getName() {
        return name;
    }

    public KickoffRequest getPrimaryRequest() {
        return requestIdToRequest.get(primaryRequestId);
    }

    public List<PairingInfo> getPairings() {
        return pairings;
    }

    public void setPairings(List<PairingInfo> pairings) {
        this.pairings = pairings;
    }

    public void addPairing(PairingInfo pairing) {
        this.pairings.add(pairing);
    }
}
