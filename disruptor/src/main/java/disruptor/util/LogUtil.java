package disruptor.util;

public class LogUtil {

    public static void logWithThreadName(String message){
        LogUtil.logWithThreadName(message + " " + Thread.currentThread().getName());
    }
}
