package disruptor.v4;

import java.util.concurrent.Executor;

public interface ConsumerInfo {

    void start(Executor executor);
}
