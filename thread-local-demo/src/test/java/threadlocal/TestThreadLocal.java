package threadlocal;

import org.junit.Test;
import threadlocal.constants.PerformanceTestingConfig;
import threadlocal.util.PerformanceTestingUtil;

public class TestThreadLocal {

    @Test
    public void testMyThreadLocal() throws Exception {
        System.out.println(PerformanceTestingUtil.testThreadLocal(
            MyThreadLocal.class,
            PerformanceTestingConfig.threads,
            PerformanceTestingConfig.threadLocalNum,
            PerformanceTestingConfig.repeatNum));
    }

    @Test
    public void testJDKThreadLocal() throws Exception {
        System.out.println(PerformanceTestingUtil.testThreadLocal(
            JDKThreadLocalAdapter.class,
            PerformanceTestingConfig.threads,
            PerformanceTestingConfig.threadLocalNum,
            PerformanceTestingConfig.repeatNum));
    }

    @Test
    public void testNettyFastThreadLocal() throws Exception {
        System.out.println(PerformanceTestingUtil.testThreadLocal(
            NettyFastThreadLocalAdapter.class,
            PerformanceTestingConfig.threads,
            PerformanceTestingConfig.threadLocalNum,
            PerformanceTestingConfig.repeatNum));
    }
}
