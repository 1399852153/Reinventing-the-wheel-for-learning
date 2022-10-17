import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolTest {

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = getThread();
        System.out.println(t1);
    }

    public static Thread getThread() throws InterruptedException {
        Thread t = new Thread(()->{
            System.out.println(666);
        });
        t.start();

        Thread.sleep(1000L);
        return t;
    }
}
