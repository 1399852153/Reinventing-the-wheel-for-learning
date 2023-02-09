package timewheel.hierarchical.v2;

public class MyHierarchicalHashedTimerV2 {

    /**
     * 关联的最底层时间轮
     * */
    private MyHierarchicalHashedTimeWheelV2 lowestTimeWheel;

    /**
     * 时间轮的启动时间（单位:纳秒）
     * */
    private Long startTime;

    /**
     * 每次tick的间隔（单位:纳秒）
     * */
    private Long perTickTime;

    /**
     * 时间轮的大小
     * */
    private int timeWheelSize;


}
