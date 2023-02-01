package timewheel.hierarchical.v1;

import timewheel.MyTimeoutTaskNode;

import java.util.concurrent.Executor;

public class MyHierarchicalHashedTimeWheelV1 {

    private final MyHierarchyHashedTimeWheelBucketV1[] ringBucketArray;

    /**
     * 总tick数
     * */
    private final long totalTick = 0;

    /**
     * 时间轮每次转动的时间(单位：纳秒nanos)
     * (perTickTime越短，调度会更精确，但cpu开销也会越大)
     * */
    private final long perTickTime;

    /**
     * 上一层时间跨度更大的时间轮
     * */
    private MyHierarchicalHashedTimeWheelV1 overFlowWheel;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    public MyHierarchicalHashedTimeWheelV1(int ringArraySize,long perTickTime, Executor taskExecutor) {
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV1[ringArraySize];
        for(int i=0; i<ringArraySize; i++){
            // 初始化，填充满时间轮唤醒数组
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV1();
        }

        this.perTickTime = perTickTime;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 当前时间轮加入任务(溢出的话，则需要放到上一层的时间轮中)
     * */
    public void addTimeoutTask(MyTimeoutTaskNode myTimeoutTaskNode){

    }

    public void advanceClockByTick(){

    }
}
