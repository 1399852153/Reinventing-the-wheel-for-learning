package timewheel;

import java.util.concurrent.TimeUnit;

public interface Timer {

    void startTimeWheel();

    void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit);
}
