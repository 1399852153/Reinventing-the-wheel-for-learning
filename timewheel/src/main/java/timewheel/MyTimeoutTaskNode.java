package timewheel;

public class MyTimeoutTaskNode {

    /**
     * 任务具体的到期时间(绝对时间)
     * */
    private long deadline;

    /**
     * 存储在时间轮中，需要等待的轮次
     * (rounds在初始化后，每次时间轮转动一周便自减1，当减为0时便代表当前任务需要被调度)
     * */
    private long rounds;

    /**
     * 创建任务时，用户指定的到期时进行调度的任务
     * */
    private Runnable targetTask;

    public long getDeadline() {
        return deadline;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public long getRounds() {
        return rounds;
    }

    public void setRounds(long rounds) {
        this.rounds = rounds;
    }

    public Runnable getTargetTask() {
        return targetTask;
    }

    public void setTargetTask(Runnable targetTask) {
        this.targetTask = targetTask;
    }
}
