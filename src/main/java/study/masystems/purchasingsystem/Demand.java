package study.masystems.purchasingsystem;

import java.util.HashMap;
import java.util.Map;

/**
 * Buyer demand for communication between Buyer and Customer.
 */
public class Demand {
    private String purchaseName;
    private Map<String, Integer> orders = new HashMap<>();

    public Demand() {
    }

    public Demand(String purchaseName) {
        this.purchaseName = purchaseName;
    }

    public void put(String good, int quantity) {
        orders.put(good, quantity);
    }

    public String getPurchaseName() {
        return purchaseName;
    }

    public void setPurchaseName(String purchaseName) {
        this.purchaseName = purchaseName;
    }

    public Map<String, Integer> getOrders() {
        return orders;
    }

    public void setOrders(Map<String, Integer> orders) {
        this.orders = orders;
    }
}