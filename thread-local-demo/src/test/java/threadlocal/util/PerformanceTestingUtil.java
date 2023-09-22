package threadlocal.util;

import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.FastThreadLocalThread;
import threadlocal.MyThreadLocal;
import threadlocal.api.ThreadLocalApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PerformanceTestingUtil {

    public static long testThreadLocal(Class<? extends ThreadLocalApi> clazz, int threads, int threadLocalNum, int repeatNum) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(threads);

        List<ThreadLocalApi<String>> threadLocalApiList = createThreadLocal(clazz,threadLocalNum);

        ExecutorService executorService = Executors.newFixedThreadPool(threads, new DefaultThreadFactory("performance-testing"));

        long start = System.currentTimeMillis();
        for(int i=0; i<threads; i++){
            executorService.execute(()->{
                for(ThreadLocalApi<String> threadLocal : threadLocalApiList){
                    for(int j=0; j<repeatNum; j++){
                        threadLocal.set("aaaaaa" + j);
                    }
                }

                countDownLatch.countDown();
            });
        }

        countDownLatch.await();
        long end = System.currentTimeMillis();

        executorService.shutdown();
        return end-start;
    }

    private static List<ThreadLocalApi<String>> createThreadLocal(Class<? extends ThreadLocalApi> clazz,int num) throws InstantiationException, IllegalAccessException {
        List<ThreadLocalApi<String>> threadLocalApiList = new ArrayList<>();
        for(int i=0; i<num; i++){
            threadLocalApiList.add(clazz.newInstance());
        }

        return threadLocalApiList;
    }
}
