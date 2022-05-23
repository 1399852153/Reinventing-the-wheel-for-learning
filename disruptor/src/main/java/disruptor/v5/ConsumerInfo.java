package disruptor.v5;

import java.util.concurrent.Executor;

public interface ConsumerInfo {

    void start(Executor executor);
}
