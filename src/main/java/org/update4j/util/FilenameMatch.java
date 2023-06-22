package org.update4j.util;

import org.update4j.OS;

public class FilenameMatch {
	
    private OS os;
    private String arch;
    
    public FilenameMatch(OS os, String arch) {
        this.os = os;
        this.arch = arch;
    }

    public OS getOs() {
        return os;
    }

    public String getArch() {
        return arch;
    }
}
