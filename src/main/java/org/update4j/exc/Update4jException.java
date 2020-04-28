package org.update4j.exc;

public class Update4jException extends RuntimeException {
    public Update4jException() {
    }

    public Update4jException(String message) {
        super(message);
    }

    public Update4jException(String message, Throwable cause) {
        super(message, cause);
    }

    public Update4jException(Throwable cause) {
        super(cause);
    }

    public Update4jException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
