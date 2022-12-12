package threadpool;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface MyScheduledExecutorService{

    /**
     * 提交一个只执行一次的任务command，在延迟delay时间后执行
     * @param command 任务对象
     * @param delay 延迟时间
     * @param unit 延迟时间单位
     * */
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit);

    /**
     * 提交一个周期性执行的任务command，在延迟initialDelay后第一次执行，随后每间隔period执行一次
     * 举个例子：第一次执行时间为initialDelay，第二次执行时间为initialDelay + (period*1)，第三次执行时间为initialDelay + (period*2)以次类推
     * 注意：任务执行过程中不能有异常，否则将会导致停止后续的调度
     * @param command 任务对象
     * @param initialDelay 第一次执行的延迟时间
     * @param period 间隔执行时间
     * @param unit 时间单位
     * */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);

    /**
     * 提交一个周期性执行的任务command，在延迟initialDelay后第一次执行，随后在每一次任务执行完成后，延迟delay后开启下一次任务
     * （区别于scheduleAtFixedRate，下一次执行时间取决于上一次执行的完成时间，所以不是fixedRate）
     * 注意：任务执行过程中不能有异常，否则将会导致停止后续的调度
     * @param command 任务对象
     * @param initialDelay 第一次执行的延迟时间
     * @param delay 距离上一次执行的延迟时间
     * @param unit 时间单位
     * */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);
}
