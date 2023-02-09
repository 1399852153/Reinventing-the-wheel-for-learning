package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MyHierarchicalHashedTimerV2 {

    /**
     * 关联的最底层时间轮
     * */
    private MyHierarchicalHashedTimeWheelV2 lowestTimeWheel;

    /**
     * 时间轮的启动时间（单位:纳秒）
     * */
    private long startTime;

    /**
     * 每次tick的间隔（单位:纳秒）
     * */
    private long perTickTime;

    /**
     * 时间轮的大小
     * */
    private int timeWheelSize;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private Executor taskExecutor;

    public void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit){
        long deadline = System.nanoTime() + timeUnit.toNanos(delayTime);

        // Guard against overflow.
        if (delayTime > 0 && deadline < 0) {
            deadline = Long.MAX_VALUE;
        }

        MyTimeoutTaskNode newTimeoutTaskNode = new MyTimeoutTaskNode();
        newTimeoutTaskNode.setTargetTask(task);
        newTimeoutTaskNode.setDeadline(deadline);

        // 加入到最底层的时间轮中，当前时间轮放不下的会溢出都上一层时间轮
        this.lowestTimeWheel.addTimeoutTask(newTimeoutTaskNode);
    }
}
