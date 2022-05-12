package disruptor.model;

import disruptor.api.MyEventConsumer;
import disruptor.util.LogUtil;

import java.util.Stack;

public class OrderEventConsumer2 implements MyEventConsumer<OrderModel> {

    private Stack<Integer> priceOrderStack = new Stack<>();
    private int maxConsumeTime;
    private boolean notOrdered = false;

    public OrderEventConsumer2(int maxConsumeTime) {
        this.maxConsumeTime = maxConsumeTime;
    }

    @Override
    public void consume(OrderModel event, long sequence, boolean endOfBatch) {
        if(!priceOrderStack.isEmpty() && event.getPrice() < priceOrderStack.peek()){
            LogUtil.logWithThreadName("price not ordered event=" + event);
            this.notOrdered = true;
            throw new RuntimeException("price not ordered event=" + event);
        }else{
            priceOrderStack.push(event.getPrice());
        }
        LogUtil.logWithThreadName("OrderEventConsumer2 消费订单事件：sequence=" + sequence + "     " + event);

        if(this.priceOrderStack.size() == this.maxConsumeTime){
            if(this.notOrdered){
                LogUtil.logWithThreadName("OrderEventConsumer2 消费订单事件失败");
            }else{
                LogUtil.logWithThreadName("OrderEventConsumer2 消费订单事件完毕" + priceOrderStack);
            }
        }
    }
}
