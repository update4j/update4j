package org.update4j.util;

import org.update4j.OS;

import java.io.File;
import java.util.logging.Logger;

public class PathUtils {

    private final static Logger log= Logger.getLogger(PathUtils.class.getName());

    /**
     * A string pointing to the os specific path application data is usually stored.<br>
     * This path points to:
     * <ul>
     *     <li>macOS - {userhome}/Library/Application Support</li>
     *     <li>Windows - %LOCALAPPDATA%</li>
     *     <li>macOS - {userhome}/.appData</li>
     * </ul>
     */
    public final static String stdUserAppData;
    public final static String bootJar;

    static{
        //stdUserAppData
        OS os= OS.CURRENT;
        if(OS.CURRENT == OS.WINDOWS){
            stdUserAppData = System.getenv("LOCALAPPDATA");
        } else if(OS.CURRENT == OS.MAC){
            stdUserAppData = System.getProperty("user.home")+"/Library/Application Support";
        } else if(OS.CURRENT == OS.LINUX){
            // not standard but ensures, that it is a hidden file and at least update4j
            // doesn't pollute the home directory...
            stdUserAppData = System.getProperty("user.home")+"/.appData";
        } else { //other
            stdUserAppData = System.getProperty("user.home");
        }

        //bootJar
        String tmp = null;
        try {
            tmp = new File(PathUtils.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile().getAbsolutePath();
        } catch (Exception e) {
            log.warning("Could not retrieve path of update4j jar file: "+e.getMessage());
        }
        bootJar = tmp;

        setVars();
    }

    /**
     * This method initializes the path variables
     */
    public static void init(){} //static init...

    public static void setVars(){
        //log.info("stdUserAppData is: "+stdUserAppData);
        if(stdUserAppData != null)
            System.setProperty("stdUserAppData",stdUserAppData);
        //log.info("bootJar is: "+bootJar);
        if(bootJar != null)
            System.setProperty("bootJar",bootJar);
    }

}
