package com.jobscout.common;

public class JobScoutException extends RuntimeException {
    private final ErrorCode code;
    private final boolean retryable;

    public JobScoutException(ErrorCode code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public ErrorCode code() { return code; }
    public boolean retryable() { return retryable; }
}
