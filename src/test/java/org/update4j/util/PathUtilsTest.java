package org.update4j.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathUtilsTest {


    @Test
    public void stdUserAppDataTest(){
        //just check that starts user home, that is true for all platforms...
        assertTrue(PathUtils.stdUserAppData.indexOf(System.getProperty("user.home")) == 0,
                "User path not included in local application data directory");
    }

}
