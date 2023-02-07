package timewheel.hierarchical.v2;

import timewheel.MyTimeoutTaskNode;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class MyHierarchicalHashedTimeWheelV2 {

    private final MyHierarchyHashedTimeWheelBucketV2[] ringBucketArray;

    /**
     * 当前时间轮所能承载的时间间隔
     * */
    private final long interval;

    private final int level;

    /**
     * 上一层时间跨度更大的时间轮
     * */
    private MyHierarchicalHashedTimeWheelV2 overFlowWheel;

    /**
     * 用于实际执行到期任务的线程池
     * */
    private final Executor taskExecutor;

    /**
     * 当前时间轮的绝对时间
     * */
    private long currentTime;

    public MyHierarchicalHashedTimeWheelV2(int ringArraySize, long currentTime, long perTickTime, Executor taskExecutor, int level) {
        this.ringBucketArray = new MyHierarchyHashedTimeWheelBucketV2[ringArraySize];
        for(int i=0; i<ringArraySize; i++){
            // 初始化，填充满时间轮唤醒数组
            this.ringBucketArray[i] = new MyHierarchyHashedTimeWheelBucketV2();
        }

        this.currentTime = currentTime;
        this.taskExecutor = taskExecutor;
        this.interval = perTickTime * ringArraySize;
        this.level = level;
    }

    /**
     * 当前时间轮加入任务(溢出的话，则需要放到上一层的时间轮中)
     * */
    public void addTimeoutTask(long currentTime, MyTimeoutTaskNode timeoutTaskNode){
        long deadline = timeoutTaskNode.getDeadline();

//        if()
    }

    public void advanceClockByTick(MyHierarchyHashedTimeWheelBucketV2 bucket,Consumer<MyTimeoutTaskNode> flushInLowerWheelFn){
        if(this.level == 0){
            // 如果是最底层的时间轮，将当前tick下命中的bucket中的任务丢到taskExecutor中执行
            bucket.expireTimeoutTask(this.taskExecutor);
        }else{
            // 如果不是最底层的时间轮，将当前tick下命中的bucket中的任务交给下一层的时间轮
            // 这里转交到下一层有两种方式：第一种是从上到下的转交，另一种是当做新任务一样还是从最下层的时间轮开始放，放不下再往上溢出
            // 选用后一种逻辑，最大的复用已有的创建新任务的逻辑，会好理解一点
            bucket.flush(flushInLowerWheelFn);
        }

        // 计算当前是否需要推进上一层的时间轮
        this.overFlowWheel.advanceClockByTick(bucket,flushInLowerWheelFn);
    }

    @Override
    public String toString() {
        return "MyHierarchicalHashedTimeWheelV2{" +
            "ringBucketArray=" + Arrays.toString(ringBucketArray) + " " +
            "level=" + this.level +
            '}';
    }
}
