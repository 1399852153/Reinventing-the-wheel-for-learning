package disruptor.model;

import disruptor.api.MyEventProducer;

public class OrderEventProducer implements MyEventProducer<OrderModel> {
    @Override
    public OrderModel newInstance() {
        return new OrderModel();
    }
}
