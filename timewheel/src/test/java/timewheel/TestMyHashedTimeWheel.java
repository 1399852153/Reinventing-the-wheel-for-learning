package timewheel;

import timewheel.model.ExecuteTimeValidTask;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestMyHashedTimeWheel {
    public static void main(String[] args) {
        long perTickTime = 100;
        MyHashedTimeWheel myHashedTimeWheel = new MyHashedTimeWheel(
            32, TimeUnit.MILLISECONDS.toNanos(perTickTime),
            Executors.newFixedThreadPool(10));

        myHashedTimeWheel.startTimeWheel();

        for(int i=0; i<100; i++) {
            double allowableMistake = perTickTime * 2;
            myHashedTimeWheel.newTimeoutTask(
                new ExecuteTimeValidTask(i,TimeUnit.SECONDS,allowableMistake),
                i, TimeUnit.SECONDS);
        }
    }
}
