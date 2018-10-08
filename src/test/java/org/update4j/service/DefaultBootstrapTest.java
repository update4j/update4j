package org.update4j.service;

import org.junit.jupiter.api.Test;
import org.update4j.util.PathUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultBootstrapTest {

    @Test
    public void replaceVariablesTest(){
        List<String> args=new ArrayList<>();
        args.add("--remote="+new File(".").getAbsolutePath());
        args.add("--local=${stdUserAppData}/update4j.xml");

        DefaultBootstrap dut = new DefaultBootstrap();
        dut.parseArgs(args);

        assertTrue(dut.getLocal().indexOf(PathUtils.stdUserAppData) >-1,
                "Variable placeholder in local has not been replaced");

        args=new ArrayList<>();
        args.add("--remote="+new File(".").getAbsolutePath());
        args.add("--local=${bootJar}/update4j.xml");

        dut = new DefaultBootstrap();
        dut.parseArgs(args);

        assertTrue(dut.getLocal().indexOf(PathUtils.bootJar) >-1,
                "Variable placeholder in local has not been replaced");
    }
}
