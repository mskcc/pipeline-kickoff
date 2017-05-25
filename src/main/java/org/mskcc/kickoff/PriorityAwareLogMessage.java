package org.mskcc.kickoff;

import org.apache.log4j.Priority;

public class PriorityAwareLogMessage {
    private final Priority priority;
    private final String message;

    public PriorityAwareLogMessage(Priority priority, String message) {
        this.priority = priority;
        this.message = message;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getMessage() {
        return message;
    }
}
