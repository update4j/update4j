package org.update4j;

import java.nio.file.Path;
import java.security.PublicKey;

import org.update4j.inject.Injectable;
import org.update4j.service.UpdateHandler;

public class UpdateOptions<T extends UpdateOptions<T>> {

    private UpdateOptions() {
    }

    private PublicKey publicKey;
    private UpdateHandler updateHandler;
    private Injectable injectable;

    @SuppressWarnings("unchecked")
    public T publicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
        return (T) this;
    }
    
    @SuppressWarnings("unchecked")
    public T updateHandler(UpdateHandler updateHandler) {
        this.updateHandler = updateHandler;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T injectable(Injectable injectable) {
        this.injectable = injectable;
        return (T) this;
    }
    
    public PublicKey getPublicKey() {
        return publicKey;
    }
    
    public UpdateHandler getUpdateHandler() {
        return updateHandler;
    }
    
    public Injectable getInjectable() {
        return injectable;
    }

    public static ArchiveUpdateOptions archive(Path location) {
        return new ArchiveUpdateOptions(location);
    }

    public static class ArchiveUpdateOptions extends UpdateOptions<ArchiveUpdateOptions> {

        private Path location;

        private ArchiveUpdateOptions(Path location) {
            this.location = location;
        }
        
        public Path getArchiveLocation() {
            return location;
        }

    }
}
