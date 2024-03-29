package timewheel.model;

import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;
import timewheel.util.PrintDateUtil;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExecuteTimeValidTask implements Runnable, TimerTask {

    /**
     * 理论被执行时间（毫秒）
     * */
    private final long needExecuteTime;

    /**
     * 能容忍的误差（毫秒）
     * */
    private final double allowableMistakeMs;

    private final CountDownLatch countDownLatch;

    private final ErrorCollector errorCollector;

    private final int taskIndex;

    public ExecuteTimeValidTask(long delayTime, TimeUnit timeUnit,
                                double allowableMistakeMs, CountDownLatch countDownLatch,
                                ErrorCollector errorCollector, int taskIndex) {
        this.needExecuteTime = System.currentTimeMillis() + timeUnit.toMillis(delayTime);
        this.allowableMistakeMs = allowableMistakeMs;
        this.countDownLatch = countDownLatch;
        this.errorCollector = errorCollector;
        this.taskIndex = taskIndex;
    }

    @Override
    public void run() {
        runTask();
    }

    @Override
    public void run(Timeout timeout) {
        runTask();
    }

    private void runTask(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        System.out.println("ExecuteTimeValidTask execute!" + simpleDateFormat.format(new Date())
            + " needExecuteTime=" + PrintDateUtil.parseDate(TimeUnit.MILLISECONDS.toNanos(needExecuteTime))
            + " taskIndex=" + this.taskIndex
        );
        executeTimeValid();

        countDownLatch.countDown();
    }

    private void executeTimeValid(){
        long actualExecuteTime = System.currentTimeMillis();
        long diff = Math.abs(actualExecuteTime - needExecuteTime);
        if(diff > this.allowableMistakeMs){
            // 校验失败，失败次数加1
            errorCollector.addErrorCount();

            System.out.println("executeTimeValid 校验失败 " +
                "actualExecuteTime=" + new Timestamp(actualExecuteTime) + " needExecuteTime=" + new Timestamp(needExecuteTime));
        }
    }
}
