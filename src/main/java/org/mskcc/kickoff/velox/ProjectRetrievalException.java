package org.mskcc.kickoff.velox;

public class ProjectRetrievalException extends RuntimeException {
    ProjectRetrievalException(String message, Exception e) {
        super(message, e);
    }
}
