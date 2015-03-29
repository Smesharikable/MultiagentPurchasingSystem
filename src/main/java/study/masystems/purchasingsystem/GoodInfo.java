package study.masystems.purchasingsystem;

/**
 * Information about good.
 */
public class GoodInfo {
    private Double cost = 0.0;
    private int minimumOrderSize = 0;
    private int deliveryTimeDays = 0;

    public GoodInfo() {
    }

    public GoodInfo(Double cost, int minimumOrderSize, int deliveryTimeDays) {
        this.cost = cost;
        this.minimumOrderSize = minimumOrderSize;
        this.deliveryTimeDays = deliveryTimeDays;
    }

    public int getMinimumOrderSize() {
        return minimumOrderSize;
    }

    public void setMinimumOrderSize(int minimumOrderSize) {
        this.minimumOrderSize = minimumOrderSize;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public int getDeliveryTimeDays() {
        return deliveryTimeDays;
    }

    public void setDeliveryTimeDays(int deliveryTimeDays) {
        this.deliveryTimeDays = deliveryTimeDays;
    }

    @Override
    public String toString() {
        return "GoodInfo{" +
                "minimumOrderSize=" + minimumOrderSize +
                ", cost=" + cost +
                '}';
    }
}
