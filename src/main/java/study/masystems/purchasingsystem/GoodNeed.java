package study.masystems.purchasingsystem;

import java.util.SimpleTimeZone;

/**
 * Describe good need.
 */
public class GoodNeed {
    private String name = "";
    private int quantity = 0;
    private int deliveryPeriod = 0;


    public GoodNeed() {
    }

    public GoodNeed(String name, int quantity, int deliveryPeriod) {
        this.name = name;
        this.quantity = quantity;
        this.deliveryPeriod = deliveryPeriod;
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

    public int getDeliveryPeriod() {
        return deliveryPeriod;
    }

    public void setDeliveryPeriod(int deliveryPeriod) {
        if (deliveryPeriod < 0) {
            throw new IllegalArgumentException("Delivery period must be positive.");
        }
        this.deliveryPeriod = deliveryPeriod;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
