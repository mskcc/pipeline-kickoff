package org.mskcc.kickoff.domain;

import org.mskcc.domain.Recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SampleSet {
    private final String id;
    private Map<String, KickoffRequest> requestIdToRequest = new LinkedHashMap<>();
    private String primaryRequestId;
    private String baitSet;
    private String finalProjectTitle;
    private Recipe recipe;
    private String name;

    public SampleSet(String id) {
        this.id = id;
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

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }
}
