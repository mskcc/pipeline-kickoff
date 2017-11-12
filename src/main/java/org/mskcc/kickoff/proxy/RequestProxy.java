package org.mskcc.kickoff.proxy;

import org.mskcc.kickoff.domain.KickoffRequest;

public interface RequestProxy {
    KickoffRequest getRequest(String projectId) throws Exception;
}
