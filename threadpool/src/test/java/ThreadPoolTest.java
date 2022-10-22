import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolTest {

    public static void main(String[] args) throws InterruptedException {
        test1();
        test2();
    }

    /**
     * 先start，再中断
     * */
    private static void test1() throws InterruptedException {
        Thread t = new Thread(() -> {
            while(true) {
                try {
                    System.out.println("test1 before sleep");
                    Thread.sleep(2000L);
                    System.out.println("test1 after sleep");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        System.out.println("test1 before t.interrupt()");
        t.start();
        Thread.sleep(1000L);
        t.interrupt();
        System.out.println("test1 after t.interrupt()");
    }

    /**
     * 先中断，再sleep
     * */
    private static void test2() throws InterruptedException {
        Thread t = new Thread(() -> {
            while(true) {
                try {
                    System.out.println("test2 before sleep");
                    Thread.sleep(100L);
                    System.out.println("test2 after sleep");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        t.start();
        t.interrupt();
        System.out.println("test2 after t.interrupt()");
    }
}
