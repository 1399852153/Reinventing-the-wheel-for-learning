package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;
import timewheel.Timer;
import timewheel.util.PrintDateUtil;

import java.sql.Time;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyHierarchicalHashedTimerV2 implements Timer {

    /**
     * 是否已启动
     * */
    private AtomicBoolean started = new AtomicBoolean(false);

    /**
     * 关联的最底层时间轮
     * */
    private volatile MyHierarchicalHashedTimeWheelV2 lowestTimeWheel;

    /**
     * 时间轮的启动时间（单位:纳秒）
     * */
    private long startTime;

    /**
     * 每次tick的间隔（单位:纳秒）
     * */
    private final long perTickTime;

    /**
     * 时间轮的大小
     * */
    private final int timeWheelSize;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 用于存储bucket元素的延迟队列，用于解决时间轮空转的问题
     * */
    private final DelayQueue<MyHierarchyHashedTimeWheelBucketV2> bucketDelayQueue = new DelayQueue<>();

    public MyHierarchicalHashedTimerV2(int timeWheelSize,long perTickTime, Executor taskExecutor) {
        this.timeWheelSize = timeWheelSize;
        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 启动worker线程等初始化操作，必须执行完成后才能正常工作
     * (简单起见，和netty不一样不是等任务被创建时才懒加载的，必须提前启动)
     * */
    @Override
    public void startTimeWheel(){
        // 启动worker线程
        new Thread(new Worker()).start();

        while (!this.started.get()){
            // 自旋循环，等待一会
            System.out.println("自旋循环，等待一会");
        }

        System.out.println("startTimeWheel 启动完成 " + this.started.get() + " wheel" + this.lowestTimeWheel);
    }

    @Override
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

    private void advanceClock(){
        try {
            MyHierarchyHashedTimeWheelBucketV2 bucket = this.bucketDelayQueue.take();
            lowestTimeWheel.advanceClockByTick(bucket.getExpiration());
            bucket.flush((node)->{
                // 当前选中的bucket中的任务，重新插入到时间轮中
                // 1 原本处于高层的bucket中的任务会被放到更底层
                // 2 原本就处于最低一层的bucket中的任务会被直接执行
                this.lowestTimeWheel.addTimeoutTask(node);
            });
            // 将当前时间轮的数据
        } catch (Exception e) {
            // 忽略掉异常
            e.printStackTrace();
        }
    }


    private final class Worker implements Runnable {
        @Override
        public void run() {

            MyHierarchicalHashedTimerV2.this.startTime = System.nanoTime();
            System.out.println("startTime=" + PrintDateUtil.parseDate(MyHierarchicalHashedTimerV2.this.startTime));

            // 初始化最底层的时间轮
            MyHierarchicalHashedTimerV2.this.lowestTimeWheel = new MyHierarchicalHashedTimeWheelV2(
                MyHierarchicalHashedTimerV2.this.startTime,
                MyHierarchicalHashedTimerV2.this.perTickTime,
                MyHierarchicalHashedTimerV2.this.timeWheelSize,
                MyHierarchicalHashedTimerV2.this.taskExecutor,
                MyHierarchicalHashedTimerV2.this.bucketDelayQueue
            );

            // 启动
            MyHierarchicalHashedTimerV2.this.started.set(true);

            while (true){
                // 一直无限循环，不断推进时间
                advanceClock();
            }
        }
    }
}
