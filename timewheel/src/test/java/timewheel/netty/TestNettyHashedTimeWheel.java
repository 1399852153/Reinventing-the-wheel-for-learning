package timewheel.netty;

import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.TimerTask;
import timewheel.model.ExecuteTimeValidTask;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestNettyHashedTimeWheel {

    public static void main(String[] args) {
        long perTickTime = 100;
        HashedWheelTimer myHashedTimeWheel = new HashedWheelTimer(Executors.defaultThreadFactory(),
            100,TimeUnit.MILLISECONDS,32);

        // 拿netty的时间轮做下测试
        for(int i=0; i<100; i++) {
            double allowableMistake = perTickTime * 2;
            int delayTime = i;
            TimeUnit delayTimeUnit = TimeUnit.SECONDS;
            myHashedTimeWheel.newTimeout(
                new ExecuteTimeValidTask(delayTime,delayTimeUnit,allowableMistake),
                delayTime, delayTimeUnit
            );
        }
    }
}
