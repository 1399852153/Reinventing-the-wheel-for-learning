package timewheel;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestMyHashedTimeWheel {
    public static void main(String[] args) {
        MyHashedTimeWheel myHashedTimeWheel = new MyHashedTimeWheel(
            32, TimeUnit.MILLISECONDS.toNanos(100),
            Executors.newFixedThreadPool(10));

        myHashedTimeWheel.startTimeWheel();

        for(int i=0; i<50; i++) {
            myHashedTimeWheel.newTimeoutTask(
                () -> System.out.println("task execute:" + new Date()),
                i, TimeUnit.SECONDS);
        }
    }
}
