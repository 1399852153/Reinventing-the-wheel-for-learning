package timewheel;

import timewheel.hierarchical.v1.MyHierarchicalHashedTimerV1;
import timewheel.model.ExecuteTimeValidTask;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestMyHierarchicalHashedTimerV1 {

    public static void main(String[] args) {
        long perTickTime = 100;
        MyHierarchicalHashedTimerV1 myHierarchicalHashedTimerV1 = new MyHierarchicalHashedTimerV1(
            32, TimeUnit.MILLISECONDS.toNanos(perTickTime),
            Executors.newFixedThreadPool(10));

        myHierarchicalHashedTimerV1.startTimeWheel();

        for(int i=0; i<100; i++) {
            double allowableMistake = perTickTime * 2;
            myHierarchicalHashedTimerV1.newTimeoutTask(
                new ExecuteTimeValidTask(i,TimeUnit.SECONDS,allowableMistake),
                i, TimeUnit.SECONDS);
        }
    }
}
