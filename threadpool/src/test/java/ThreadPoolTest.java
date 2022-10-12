import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolTest {

    public static void main(String[] args) {
        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(5);

        threadPoolExecutor.execute(()->{
            System.out.println(666);
            System.out.println(777);
            System.out.println(888);
        });
    }
}
