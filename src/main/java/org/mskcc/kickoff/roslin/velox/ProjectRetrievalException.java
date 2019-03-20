package org.mskcc.kickoff.roslin.velox;

public class ProjectRetrievalException extends RuntimeException {
    ProjectRetrievalException(String message, Exception e) {
        super(message, e);
    }
}
