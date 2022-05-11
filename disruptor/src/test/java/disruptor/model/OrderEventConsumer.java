package disruptor.model;

import disruptor.api.MyEventConsumer;

import java.util.Set;
import java.util.Stack;

public class OrderEventConsumer implements MyEventConsumer<OrderModel> {

    private Stack<Integer> priceOrderStack = new Stack<>();
    private int maxConsumeTime;
    private boolean notOrdered = false;

    public OrderEventConsumer(int maxConsumeTime) {
        this.maxConsumeTime = maxConsumeTime;
    }

    @Override
    public void consume(OrderModel event, long sequence, boolean endOfBatch) {
        if(!priceOrderStack.isEmpty() && event.getPrice() < priceOrderStack.peek()){
            System.out.println("price not ordered event=" + event);
            this.notOrdered = true;
            throw new RuntimeException("price not ordered event=" + event);
        }else{
            priceOrderStack.push(event.getPrice());
        }
        System.out.println("OrderEventConsumer1 消费订单事件：sequence=" + sequence + "     " + event);

        if(this.priceOrderStack.size() == this.maxConsumeTime){
            if(this.notOrdered){
                System.out.println("OrderEventConsumer1 消费订单事件失败");
            }else{
                System.out.println("OrderEventConsumer1 消费订单事件完毕" + priceOrderStack);
            }
        }
    }
}
