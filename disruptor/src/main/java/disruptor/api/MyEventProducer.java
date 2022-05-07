package disruptor.api;

/**
 * 生产者初始化事件对象的接口
 * */
public interface MyEventProducer<T> {

    T newInstance();
}
