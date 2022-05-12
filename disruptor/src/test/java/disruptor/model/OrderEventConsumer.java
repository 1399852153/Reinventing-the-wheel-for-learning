package disruptor.model;

import disruptor.api.MyEventConsumer;
import disruptor.util.LogUtil;

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
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if(!priceOrderStack.isEmpty() && event.getPrice() < priceOrderStack.peek()){
            LogUtil.logWithThreadName("price not ordered event=" + event);
            this.notOrdered = true;
            throw new RuntimeException("price not ordered event=" + event);
        }else{
            priceOrderStack.push(event.getPrice());
        }
        LogUtil.logWithThreadName("OrderEventConsumer1 消费订单事件：sequence=" + sequence + "     " + event);

        if(this.priceOrderStack.size() == this.maxConsumeTime){
            if(this.notOrdered){
                LogUtil.logWithThreadName("OrderEventConsumer1 消费订单事件失败");
            }else{
                LogUtil.logWithThreadName("OrderEventConsumer1 消费订单事件完毕" + priceOrderStack);
            }
        }
    }
}
