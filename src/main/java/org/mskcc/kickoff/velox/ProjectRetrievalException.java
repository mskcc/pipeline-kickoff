package org.mskcc.kickoff.velox;

public class ProjectRetrievalException extends RuntimeException {
    public ProjectRetrievalException(String message, Exception e) {
        super(message, e);
    }
}
