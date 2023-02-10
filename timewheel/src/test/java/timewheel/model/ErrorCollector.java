package timewheel.model;

import java.util.concurrent.atomic.AtomicInteger;

public class ErrorCollector {

    private final AtomicInteger atomicInteger = new AtomicInteger();

    public void addErrorCount(){
        this.atomicInteger.incrementAndGet();
    }

    public boolean hasError(){
        return this.atomicInteger.get() > 0;
    }
}
