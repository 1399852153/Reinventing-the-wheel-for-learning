package timewheel.hierarchical;

import timewheel.MyTimeoutTaskNode;

import java.util.LinkedList;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class MyHierarchyHashedTimeWheelBucket implements Delayed {

    private final LinkedList<MyTimeoutTaskNode> linkedList = new LinkedList<>();

    public void addTimeout(MyTimeoutTaskNode timeout) {
        linkedList.add(timeout);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return 0;
    }

    @Override
    public int compareTo(Delayed o) {
        return 0;
    }
}
