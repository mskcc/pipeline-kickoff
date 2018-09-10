package org.mskcc.kickoff.retriever;

import org.mskcc.kickoff.domain.KickoffRequest;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface DataPropagator {
    void propagateRequestData(List<KickoffRequest> kickoffRequests);

    void setDesignFiles(KickoffRequest kickoffRequest, Map<String, String> projectInfo);

    void setTumorType(KickoffRequest kickoffRequest, Map<String, String> projectInfo);

    void setAssay(KickoffRequest kickoffRequest, Map<String, String> projectInfo);

    void addSamplesToPatients(KickoffRequest kickoffRequest);

    boolean isOncoTreeValid(String oncotreeCode);

    void addManualOverrides(KickoffRequest kickoffRequest);

    void setNewBaitSet(KickoffRequest kickoffRequest);

    void assignProjectSpecificInfo(KickoffRequest request);

    String findDesignFile(KickoffRequest request, String assay);

    String findDesignFileForExome(File dir, String requestId);

    String findDesignFileForImpact(KickoffRequest request, String assay, File dir);

    int getRunNumber(KickoffRequest kickoffRequest);
}
