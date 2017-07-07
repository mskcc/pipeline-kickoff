package org.mskcc.kickoff.proxy;

import org.mskcc.kickoff.domain.Request;

import java.util.List;

public interface RequestProxy {
    List<Request> getRequests(String requestId);
}
