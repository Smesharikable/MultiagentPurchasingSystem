package study.masystems.purchasingsystem;

import java.util.HashMap;
import java.util.Map;

/**
 * Purchase information for Buyer.
 * TODO: Rename to TermOfPurchase.
 */
public class PurchaseInfo {

    private int deliveryPeriodDays = 0;
    private Map<String, Double> goodsPrice;
    private Map<String, Integer> goodsRest;

    public PurchaseInfo() {
        goodsPrice = new HashMap<String, Double>();
    }

    public PurchaseInfo(int deliveryPeriod, Map<String, Double> goodsPrice) {
        this.deliveryPeriodDays = deliveryPeriod;
        this.goodsPrice = goodsPrice;
    }

    public void addGood(String name, double cost) {
        goodsPrice.put(name, cost);
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

    public Map<String, Double> getGoodsPrice() {
        return goodsPrice;
    }

    public void setGoodsPrice(Map<String, Double> goodsPrice) {
        this.goodsPrice = goodsPrice;
    }

    public Map<String, Integer> getGoodsRest() {
        return goodsRest;
    }

    public void setGoodsRest(Map<String, Integer> goodsRest) {
        this.goodsRest = goodsRest;
    }
}
