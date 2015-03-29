package study.masystems.purchasingsystem;

/**
 * Information about good.
 */
public class GoodInfo {
    private int minimumOrderSize;
    private Double cost;
    private int deliveryTimeDays;

    public GoodInfo() {
        this(0, 0.0, 0);
    }

    public GoodInfo(int minimumOrderSize, Double cost, int deliveryTimeDays) {
        this.minimumOrderSize = minimumOrderSize;
        this.cost = cost;
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
