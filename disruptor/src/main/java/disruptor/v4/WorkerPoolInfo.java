package disruptor.v4;

import java.util.concurrent.Executor;

public class WorkerPoolInfo<T> implements ConsumerInfo{

    private final WorkerPoolV4<T> workerPool;

    public WorkerPoolInfo(WorkerPoolV4<T> workerPool) {
        this.workerPool = workerPool;
    }

    @Override
    public void start(Executor executor) {
        workerPool.start(executor);
    }
}
