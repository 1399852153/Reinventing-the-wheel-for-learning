package timewheel;

import timewheel.hierarchical.v1.MyHierarchicalHashedTimerV1;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestMyHierarchicalHashedTimerV1 {

    public static void main(String[] args) throws InterruptedException {
        MyHierarchicalHashedTimerV1 myHierarchicalHashedTimerV1 = new MyHierarchicalHashedTimerV1(
            3, TimeUnit.MILLISECONDS.toNanos(1000),
            Executors.newFixedThreadPool(10));

        myHierarchicalHashedTimerV1.startTimeWheel();

        Thread.sleep(1500L);
        for(int i=0; i<50; i++) {
            int index = i;
            myHierarchicalHashedTimerV1.newTimeoutTask(
                () -> System.out.println("task execute: i=" + index + " " + new Date()),
                i, TimeUnit.SECONDS);
        }
    }
}
