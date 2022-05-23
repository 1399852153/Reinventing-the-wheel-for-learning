package disruptor.v5;

import java.util.concurrent.Executor;

public class WorkerPoolInfo<T> implements ConsumerInfo {

    private final WorkerPoolV5<T> workerPool;

    public WorkerPoolInfo(WorkerPoolV5<T> workerPool) {
        this.workerPool = workerPool;
    }

    @Override
    public void start(Executor executor) {
        workerPool.start(executor);
    }
}
