package study.masystems.purchasingsystem;

/**
 *
 */
public class GoodInformation {
    private Double cost = 0.0;
    private int minimalQuantity = 0;
    private int deliveryPeriodDays = 0;

    public GoodInformation() {
    }

    public GoodInformation(Double cost, int minimalQuantity, int deliveryPeriodDays) {
        this.cost = cost;
        this.minimalQuantity = minimalQuantity;
        this.deliveryPeriodDays = deliveryPeriodDays;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public int getMinimalQuantity() {
        return minimalQuantity;
    }

    public void setMinimalQuantity(int minimalQuantity) {
        this.minimalQuantity = minimalQuantity;
    }

    public int getDeliveryPeriodDays() {
        return deliveryPeriodDays;
    }

    public void setDeliveryPeriodDays(int deliveryPeriodDays) {
        this.deliveryPeriodDays = deliveryPeriodDays;
    }

    @Override
    public String toString() {
        return "GoodInformation{" +
                "minimalQuantity=" + minimalQuantity +
                ", cost=" + cost +
                '}';
    }
}
