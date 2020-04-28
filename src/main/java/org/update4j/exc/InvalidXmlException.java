package org.update4j.exc;

public class InvalidXmlException extends Update4jException {

    public InvalidXmlException() {
    }

    public InvalidXmlException(String message) {
        super(message);
    }

    public InvalidXmlException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidXmlException(Throwable cause) {
        super(cause);
    }

    public InvalidXmlException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
