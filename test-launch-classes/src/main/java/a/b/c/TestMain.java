package a.b.c;

import org.update4j.LaunchContext;
import org.update4j.service.Launcher;

public class TestMain implements Launcher {

    public static void main(String[] args) {
        System.out.println("hej");
    }

    @Override
    public void run(LaunchContext context) {
            System.out.println("hej!!!!");
            System.getProperties().setProperty(getClass().getName(),"true");
    }
}
