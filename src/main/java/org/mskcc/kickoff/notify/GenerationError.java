package org.mskcc.kickoff.notify;

import org.mskcc.kickoff.printer.ErrorCode;

public class GenerationError {
    private final String message;
    private final ErrorCode errorCode;

    public GenerationError(String message, ErrorCode errorCode) {
        this.message = message;
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
