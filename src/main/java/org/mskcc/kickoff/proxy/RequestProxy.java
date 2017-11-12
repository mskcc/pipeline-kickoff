package org.mskcc.kickoff.proxy;

import org.mskcc.kickoff.domain.KickoffRequest;

import java.util.Optional;

public interface RequestProxy {
    KickoffRequest getRequest(String projectId) throws Exception;
}
