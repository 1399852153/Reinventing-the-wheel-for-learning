package timewheel.netty;

import org.jboss.netty.util.HashedWheelTimer;
import timewheel.Timer;
import timewheel.model.ExecuteTimeValidTask;

import java.util.concurrent.TimeUnit;

public class NettyTimeWheelAdaptor implements Timer {

    private HashedWheelTimer hashedWheelTimer;

    public NettyTimeWheelAdaptor(HashedWheelTimer hashedWheelTimer) {
        this.hashedWheelTimer = hashedWheelTimer;
    }

    @Override
    public void startTimeWheel() {
        // netty的时间轮无需初始化，直接加任务就行

        System.out.println("startTimeWheel 启动完成:" + this.getClass().getSimpleName());
    }

    @Override
    public void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit) {
        // 简单起见，强转一下
        ExecuteTimeValidTask executeTimeValidTask = (ExecuteTimeValidTask)task;
        hashedWheelTimer.newTimeout(executeTimeValidTask,delayTime,timeUnit);
    }
}
