package org.update4j;

import org.update4j.service.UpdateHandler;

public class UpdateResult {
    
    public UpdateHandler handler;
    private UpdateContext context;
    private Throwable exception;
    
    public UpdateHandler handler() {
        return handler;
    }
    
    public UpdateContext updateContext() {
        return context;
    }
    
    public Throwable exception() {
        return exception;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T result() {
        return (T) handler.getResult();
    }
}
