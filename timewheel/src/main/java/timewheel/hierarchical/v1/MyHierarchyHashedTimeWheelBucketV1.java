package timewheel.hierarchical.v1;

import timewheel.MyTimeoutTaskNode;

import java.util.LinkedList;

/**
 * 时间轮环形数组下标对应的桶(保存一个超时任务MyTimeoutTaskNode的链表)
 * */
public class MyHierarchyHashedTimeWheelBucketV1 {

    private final LinkedList<MyTimeoutTaskNode> linkedList = new LinkedList<>();

    public void addTimeout(MyTimeoutTaskNode timeout) {
        linkedList.add(timeout);
    }
}
