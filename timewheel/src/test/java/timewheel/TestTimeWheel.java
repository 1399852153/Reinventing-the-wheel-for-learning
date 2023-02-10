package timewheel;

import org.jboss.netty.util.HashedWheelTimer;
import org.junit.Test;
import timewheel.hierarchical.v1.MyHierarchicalHashedTimerV1;
import timewheel.hierarchical.v2.MyHierarchicalHashedTimerV2;
import timewheel.model.ErrorCollector;
import timewheel.model.ExecuteTimeValidTask;
import timewheel.netty.NettyTimeWheelAdaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestTimeWheel {

    /**
     * 每次tick的单位毫秒
     * */
    private static final long perTickTime = 100;

    /**
     * 时间轮的大小，兼容netty，必须是2的幂
     * */
    private static final int timeWheelSize = 8;

    /**
     * 允许的误差
     * */
    private static final double allowableMistake = perTickTime * 2;

    private static final int totalTask = 100;

    private static final long taskDelayNum = 200;
    private static final TimeUnit taskDelayUnit = TimeUnit.MILLISECONDS;


    @Test
    public void testMyHashedTimeWheel() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        MyHashedTimeWheel myHashedTimeWheel = new MyHashedTimeWheel(
            timeWheelSize, TimeUnit.MILLISECONDS.toNanos(perTickTime),
            executorService);

        testTimer(myHashedTimeWheel);
    }

    @Test
    public void testMyHierarchicalHashedTimerV1() throws InterruptedException {
        MyHierarchicalHashedTimerV1 myHierarchicalHashedTimerV1 = new MyHierarchicalHashedTimerV1(
            timeWheelSize, TimeUnit.MILLISECONDS.toNanos(perTickTime),
            Executors.newFixedThreadPool(10));

        testTimer(myHierarchicalHashedTimerV1);
    }

    @Test
    public void testMyHierarchicalHashedTimerV2() throws InterruptedException {
        MyHierarchicalHashedTimerV2 myHierarchicalHashedTimerV2 = new MyHierarchicalHashedTimerV2(
            timeWheelSize, TimeUnit.MILLISECONDS.toNanos(perTickTime),
            Executors.newFixedThreadPool(10));

        testTimer(myHierarchicalHashedTimerV2);
    }

    @Test
    public void testNettyHashedTimeWheel() throws InterruptedException {
        HashedWheelTimer nettyTimeWheel = new HashedWheelTimer(Executors.defaultThreadFactory(),
            perTickTime,TimeUnit.MILLISECONDS,timeWheelSize);

        testTimer(new NettyTimeWheelAdaptor(nettyTimeWheel));
    }

    private static void testTimer(Timer timer) throws InterruptedException {
        ErrorCollector errorCollector = new ErrorCollector();
        CountDownLatch countDownLatch = new CountDownLatch(totalTask);

        timer.startTimeWheel();

        for(int i=0; i<100; i++) {
            ExecuteTimeValidTask validTask = new ExecuteTimeValidTask(
                i*taskDelayNum, taskDelayUnit,allowableMistake, countDownLatch, errorCollector,i);

            timer.newTimeoutTask(validTask, i*taskDelayNum, taskDelayUnit);
        }

        countDownLatch.await();
        if(errorCollector.hasError()){
            throw new RuntimeException("testTimer error timer=" + timer.getClass().getName());
        }
    }
}
