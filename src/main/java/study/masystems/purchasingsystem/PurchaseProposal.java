package study.masystems.purchasingsystem;

import jade.core.AID;

/**
 * Information about good.
 */
public class PurchaseProposal {
    private AID supplier;
    private Double cost = 0.0;
    private int minimalQuantity = 0;
    private int deliveryPeriodDays = 0;

    public PurchaseProposal() {
    }

    public PurchaseProposal(AID supplier, GoodInformation goodInformation) {
        this.supplier = supplier;
        this.cost = goodInformation.getCost();
        this.minimalQuantity = goodInformation.getMinimalQuantity();
        this.deliveryPeriodDays = goodInformation.getDeliveryPeriodDays();
    }

    public PurchaseProposal(AID supplier, Double cost, int minimumOrderSize, int deliveryPeriodDays) {
        this.supplier = supplier;
        this.cost = cost;
        this.minimalQuantity = minimumOrderSize;
        this.deliveryPeriodDays = deliveryPeriodDays;
    }

    public AID getSupplier() {
        return supplier;
    }

    public void setSupplier(AID supplier) {
        this.supplier = supplier;
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
        return "PurchaseProposal{" +
                "minimalQuantity=" + minimalQuantity +
                ", cost=" + cost +
                '}';
    }
}
