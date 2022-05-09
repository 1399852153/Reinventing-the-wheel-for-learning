package disruptor.model;

import disruptor.api.MyEventConsumer;

import java.util.Set;
import java.util.Stack;

public class OrderEventConsumer implements MyEventConsumer<OrderModel> {

    private Stack<Integer> priceOrderStack = new Stack<>();

    @Override
    public void consume(OrderModel event, long sequence, boolean endOfBatch) {
        if(!priceOrderStack.isEmpty() && event.getPrice() < priceOrderStack.peek()){
            throw new RuntimeException("price not ordered event=" + event);
        }else{
            priceOrderStack.push(event.getPrice());
        }
        System.out.println("消费订单事件：sequence=" + sequence + "     " + event);
    }
}
