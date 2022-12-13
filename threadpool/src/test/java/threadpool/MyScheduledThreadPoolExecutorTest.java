package threadpool;

import org.junit.Test;
import threadpool.blog.MyScheduledThreadPoolExecutor;
import threadpool.blog.MyThreadPoolExecutorV2;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MyScheduledThreadPoolExecutorTest {

    @Test
    public void testOneAction() throws InterruptedException {
        MyScheduledThreadPoolExecutor myScheduledThreadPoolExecutor = new MyScheduledThreadPoolExecutor(
                10,Executors.defaultThreadFactory(),new MyThreadPoolExecutorV2.MyAbortPolicy());

        CountDownLatch countDownLatch = new CountDownLatch(2);
        {
            // 一次性的任务验证
            Date before = new Date();
            int delay = 2;
            myScheduledThreadPoolExecutor.schedule(() -> {
                Date after = new Date();
                scheduledTimeValid(before, after, delay);
                System.out.println("testOneAction 6");

                countDownLatch.countDown();
            }, delay, TimeUnit.SECONDS);
        }

        {
            // 一次性的任务验证
            Date before = new Date();
            int delay = 3;
            myScheduledThreadPoolExecutor.schedule(() -> {
                Date after = new Date();
                scheduledTimeValid(before, after, delay);
                System.out.println("testOneAction 6");

                countDownLatch.countDown();
            }, delay, TimeUnit.SECONDS);
        }

        countDownLatch.await();
    }

    @Test
    public void testScheduleAtFixedRate() throws InterruptedException {
        MyScheduledThreadPoolExecutor myScheduledThreadPoolExecutor = new MyScheduledThreadPoolExecutor(
                10,Executors.defaultThreadFactory(),new MyThreadPoolExecutorV2.MyAbortPolicy());

        CountDownLatch countDownLatch = new CountDownLatch(1);

        // 固定周期的任务验证
        int delay = 2;
        int fixedRate = 1;
        int repeat = 5;

        // 简单起见就不创建类了
        Date before = new Date();
        Map<String,Object> context = new HashMap<>();
        context.put("isFirst",true);
        context.put("currentRepeat",0);

        myScheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
            int currentRepeat = (int) context.get("currentRepeat");
            if(currentRepeat >= repeat){
                // 重复次数到了，结束
                countDownLatch.countDown();
                return;
            }

            // 执行次数+1
            context.put("currentRepeat",currentRepeat+1);
            boolean isFirst = (boolean) context.get("isFirst");
            Date after = new Date();
            if(isFirst){
                // 第一次执行，看delay
                scheduledTimeValid(before, after, delay);
                // 不是第一次执行
                context.put("isFirst",false);
            }else{
                // 后续的调度，看fixedRate
                scheduledTimeValid(before, after, delay + fixedRate * currentRepeat);
            }
            System.out.println("testScheduleAtFixedRate 6:" + after);

            // 不是很准，但也够了
            context.put("before", new Date());
        }, delay, fixedRate, TimeUnit.SECONDS);

        countDownLatch.await();
    }


    @Test
    public void testScheduleWithFixedDelay() throws InterruptedException {
        MyScheduledThreadPoolExecutor myScheduledThreadPoolExecutor = new MyScheduledThreadPoolExecutor(
                10,Executors.defaultThreadFactory(),new MyThreadPoolExecutorV2.MyAbortPolicy());

        CountDownLatch countDownLatch = new CountDownLatch(1);

        // 固定周期的任务验证
        int delay = 3;
        int fixedDelay = 2;
        int repeat = 5;

        // 简单起见就不创建类了
        Map<String,Object> context = new HashMap<>();
        context.put("isFirst",true);
        context.put("before",new Date());
        context.put("currentRepeat",0);

        myScheduledThreadPoolExecutor.scheduleWithFixedDelay(() -> {
            int currentRepeat = (int) context.get("currentRepeat");
            if(currentRepeat >= repeat){
                // 重复次数到了，结束
                countDownLatch.countDown();
                return;
            }

            // 执行次数+1
            context.put("currentRepeat",currentRepeat+1);
            boolean isFirst = (boolean) context.get("isFirst");
            Date before = (Date) context.get("before");
            Date after = new Date();
            if(isFirst){
                // 第一次执行，看delay
                scheduledTimeValid(before, after, delay);
                // 不是第一次执行
                context.put("isFirst",false);
            }else{

                // 后续的调度，看fixedRate
                scheduledTimeValid(before, after, fixedDelay);
            }
            System.out.println("testScheduleWithFixedDelay 6:" + after);

            // 不是很准，但也够了
            context.put("before", new Date());
        }, delay, fixedDelay, TimeUnit.SECONDS);

        countDownLatch.await();
    }

    private void scheduledTimeValid(Date before, Date after, int intervalSecond){
        // 允许误差在0.1s以内
        long mistake = TimeUnit.SECONDS.toMillis( 1) / 10;
        long diff = after.getTime() - before.getTime();

        long intervalNanos = TimeUnit.SECONDS.toMillis(intervalSecond);
        if(diff < intervalNanos || diff > intervalNanos + mistake){
            System.out.println("超过误差 before=" + before + " after=" + after);
            throw new RuntimeException("超过误差");
        }
    }
}
