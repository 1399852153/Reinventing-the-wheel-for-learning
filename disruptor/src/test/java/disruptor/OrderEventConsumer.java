package disruptor;

import disruptor.api.MyEventConsumer;
import disruptor.model.OrderModel;

public class OrderEventConsumer implements MyEventConsumer<OrderModel> {

    @Override
    public void consume(OrderModel event, long sequence, boolean endOfBatch) {
        System.out.println("消费订单事件：sequence=" + sequence + "     " + event);
    }
}
