package study.masystems.purchasingsystem;

/**
 * Describe good need.
 */
public class GoodNeed {
    private int quantity = 0;
    private int deliveryPeriodDays = 0;


    public GoodNeed() {
    }

    public GoodNeed(int quantity, int deliveryPeriodDays) {
        this.quantity = quantity;
        this.deliveryPeriodDays = deliveryPeriodDays;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
        this.quantity = quantity;
    }

    public int getDeliveryPeriodDays() {
        return deliveryPeriodDays;
    }

    public void setDeliveryPeriodDays(int deliveryPeriodDays) {
        if (deliveryPeriodDays < 0) {
            throw new IllegalArgumentException("Delivery period must be positive.");
        }
        this.deliveryPeriodDays = deliveryPeriodDays;
    }

}
