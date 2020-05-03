package a.b.c;

public class TestMain {

    public static void main(String[] args) {
        System.out.println("hej!!!!");
        System.getProperties()
              .setProperty(TestMain.class.getName(), "true");
    }

}
