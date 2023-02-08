package timewheel.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class PrintDateUtil {

    public static String parseDate(long nanosTime){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");

        Date date = new Date(TimeUnit.NANOSECONDS.toMillis(nanosTime));
        return simpleDateFormat.format(date);
    }
}
