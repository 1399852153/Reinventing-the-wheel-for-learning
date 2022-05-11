package disruptor.util;

public class LogUtil {

    public static void logWithThreadName(String message){
        System.out.println(message + " " + Thread.currentThread().getName());
    }
}
