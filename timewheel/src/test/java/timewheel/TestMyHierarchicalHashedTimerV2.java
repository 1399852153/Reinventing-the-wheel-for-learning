package timewheel;

import timewheel.hierarchical.v2.MyHierarchicalHashedTimerV2;
import timewheel.model.ExecuteTimeValidTask;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestMyHierarchicalHashedTimerV2 {

    public static void main(String[] args) {
        long perTickTime = 1000;
        MyHierarchicalHashedTimerV2 myHierarchicalHashedTimerV2 = new MyHierarchicalHashedTimerV2(
            10, TimeUnit.MILLISECONDS.toNanos(perTickTime),
            Executors.newFixedThreadPool(10));

        myHierarchicalHashedTimerV2.startTimeWheel();

        for(int i=0; i<50; i++) {
            double allowableMistake = perTickTime * 2;
            myHierarchicalHashedTimerV2.newTimeoutTask(
                new ExecuteTimeValidTask(i,TimeUnit.SECONDS,allowableMistake),
                i, TimeUnit.SECONDS);
        }
    }
}
