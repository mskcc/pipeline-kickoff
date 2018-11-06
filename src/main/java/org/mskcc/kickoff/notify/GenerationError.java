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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GenerationError that = (GenerationError) o;

        if (message != null ? !message.equals(that.message) : that.message != null) return false;
        return errorCode == that.errorCode;
    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (errorCode != null ? errorCode.hashCode() : 0);
        return result;
    }
}
