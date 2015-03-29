package study.masystems.purchasingsystem;

import java.util.HashMap;
import java.util.Map;

/**
 * Purchase information for Buyer.
 */
public class PurchaseInfo {

    private int deliveryPeriod = 0;
    private Map<String, Double> goodsPrice;

    public PurchaseInfo() {
        goodsPrice = new HashMap<String, Double>();
    }

    public PurchaseInfo(int deliveryPeriod, Map<String, Double> goodsPrice) {
        this.deliveryPeriod = deliveryPeriod;
        this.goodsPrice = goodsPrice;
    }

    public void addGood(String name, double cost) {
        goodsPrice.put(name, cost);
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

    public Map<String, Double> getGoodsPrice() {
        return goodsPrice;
    }

    public void setGoodsPrice(Map<String, Double> goodsPrice) {
        this.goodsPrice = goodsPrice;
    }
}
