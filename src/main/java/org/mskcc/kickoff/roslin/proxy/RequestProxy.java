package org.mskcc.kickoff.roslin.proxy;

import org.mskcc.kickoff.roslin.domain.KickoffRequest;

public interface RequestProxy {
    KickoffRequest getRequest(String projectId) throws Exception;
}
