package softreference;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        A a = createA();
        Thread.sleep(1000);
        System.gc();
        Thread.sleep(1000);
        System.out.println(a.getUser());
    }



    private static A createA(){
        User user = new User("Tom");

        A a = new A(user);
        System.out.println(a.getUser());

        user = null;
        return a;
    }
}
