package threadlocal;

import io.netty.util.concurrent.FastThreadLocal;

public class Test {

    public static void main(String[] args) throws InterruptedException {
        for(int i=0; i<1000; i++){
            ThreadLocal<String> jdkThreadLocal = new ThreadLocal<>();
            jdkThreadLocal.set("aaa");
            System.out.println(jdkThreadLocal.get());
//            jdkThreadLocal.remove();
        }

        Thread.sleep(1000);
        System.gc();
        Thread.sleep(1000);
        ThreadLocal<Integer> jdkThreadLocal = new ThreadLocal<>();
        jdkThreadLocal.set(1);
        System.out.println("================================");


        for(int i=0; i<1000; i++){
            FastThreadLocal<String> fastThreadLocal = new FastThreadLocal<>();
            fastThreadLocal.set("aaa");
            System.out.println(fastThreadLocal.get());
//            fastThreadLocal.remove();
        }

        System.gc();
        FastThreadLocal<Integer> fastThreadLocal = new FastThreadLocal<>();
        fastThreadLocal.set(1);
        FastThreadLocal<Integer> fastThreadLocal2 = new FastThreadLocal<>();
        fastThreadLocal2.set(2);
        FastThreadLocal<Integer> fastThreadLocal3 = new FastThreadLocal<>();
        fastThreadLocal3.set(2);
        System.out.println("================================");
    }
}
