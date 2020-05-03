package a.b.c;

public class FailingTestMain {

    public static void main(String[] args) {
        System.out.println("hej!!!!");
        System.getProperties()
              .setProperty(FailingTestMain.class.getName(), "true");
        throw new RuntimeException("funkar inte");
    }

}
