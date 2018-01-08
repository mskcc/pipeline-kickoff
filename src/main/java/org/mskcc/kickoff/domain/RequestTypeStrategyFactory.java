package org.mskcc.kickoff.domain;

import org.mskcc.domain.RequestType;

import java.util.HashMap;
import java.util.Map;

public class RequestTypeStrategyFactory {
    private static final Map<RequestType, RequestTypeStrategy> requestTypeToStrategy = new HashMap<>();

    static {
        requestTypeToStrategy.put(RequestType.EXOME, new ExomeStrategy());
        requestTypeToStrategy.put(RequestType.IMPACT, new ImpactStrategy());
        requestTypeToStrategy.put(RequestType.OTHER, new OtherStrategy());
        requestTypeToStrategy.put(RequestType.RNASEQ, new RNASeqStrategy());
    }

    public RequestTypeStrategy getRequestTypeStrategy(RequestType requestType) {
        if (!requestTypeToStrategy.containsKey(requestType))
            throw new IllegalArgumentException(String.format("No strategy found for request type: %s", requestType));

        return requestTypeToStrategy.get(requestType);
    }
}
