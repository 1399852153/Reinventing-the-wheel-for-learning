package disruptor.model;

public class OrderModel {

    private String message;
    private int price;

    public OrderModel(String message, int price) {
        this.message = message;
        this.price = price;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "OrderModel{" +
                "message='" + message + '\'' +
                ", price=" + price +
                '}';
    }
}
