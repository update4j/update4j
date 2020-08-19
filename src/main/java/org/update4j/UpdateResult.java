package org.update4j;

import org.update4j.service.UpdateHandler;

public class UpdateResult {
    
    private UpdateHandler handler;
    private Throwable exception;
    
    UpdateResult(UpdateHandler handler, Throwable exception) {
        this.handler = handler;
        this.exception = exception;
    }
    
    public Throwable getException() {
        return exception;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T result() {
        return (T) handler.getResult();
    }
}
