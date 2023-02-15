package timewheel;

import java.util.concurrent.TimeUnit;

public interface Timer {

    /**
     * 启动时间轮
     * */
    void startTimeWheel();

    /**
     * 创建新的超时任务(必须先startTimeWheel完成后，才能创建新任务)
     * @param task 超时时需要调度的自定义任务
     * @param delayTime 延迟时间
     * @param timeUnit 延迟时间delayTime的单位
     * */
    void newTimeoutTask(Runnable task, long delayTime, TimeUnit timeUnit);
}
