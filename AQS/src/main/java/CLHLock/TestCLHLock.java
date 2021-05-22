package CLHLock;

/**
 * @author xiongyx
 * @date 2021/5/12
 */
public class TestCLHLock {

  private static int cnt = 0;

  public static void main(String[] args) throws Exception {
    final CLHLockDemo lock = new CLHLockDemo();

    for (int i = 0; i < 100; i++) {
      new Thread(() -> {
        lock.lock();

        cnt++;

        lock.unLock();
      }).start();
    }
    // 让main线程休眠10秒，确保其他线程全部执行完
    Thread.sleep(5000);
    System.out.println();
    System.out.println("cnt----------->>>" + cnt);

  }

}
