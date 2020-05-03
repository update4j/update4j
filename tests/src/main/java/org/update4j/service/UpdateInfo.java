package org.update4j.service;

import org.update4j.UpdateContext;

public interface UpdateInfo {
    UpdateContext getUpdateContext();
    Exception getException();
}
