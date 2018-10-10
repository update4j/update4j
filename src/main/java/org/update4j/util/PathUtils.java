package org.update4j.util;

import org.update4j.OS;

import java.util.logging.Logger;

/**
 * This class provides common locations in a platform independent form.
 * These variables are added to the system properties and can be referenced in the configuration or command line.
 */
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

        setVars();
    }

    /**
     * This method initializes the path variables. Initialization is performed during loading the class.
	 * This method can be used to ensure the class is loaded.
     */
    public static void init(){} //static init...

	/**
	 * Sets the System properties to the current value of the variables.
	 * This method is called during static initialization, so the user is not required to invoke it.
	 */
    public static void setVars(){
        //log.info("stdUserAppData is: "+stdUserAppData);
        if(stdUserAppData != null)
            System.setProperty("stdUserAppData",stdUserAppData);
    }

}
