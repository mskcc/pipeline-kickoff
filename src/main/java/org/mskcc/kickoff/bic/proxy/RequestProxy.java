package org.mskcc.kickoff.bic.proxy;

import org.mskcc.kickoff.bic.domain.KickoffRequest;

import java.util.List;

public interface RequestProxy {
    List<KickoffRequest> getRequests(String requestId);
}
