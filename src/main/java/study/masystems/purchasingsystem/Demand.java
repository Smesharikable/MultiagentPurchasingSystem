package study.masystems.purchasingsystem;

/**
 * Buyer demand for communication between Buyer and Customer.
 */
public class Demand {
    private String good = null;
    private int count = 0;

    public Demand() {
    }

    public Demand(String good, int count) {
        this.good = good;
        this.count = count;
    }

    public String getGood() {
        return good;
    }

    public void setGood(String good) {
        this.good = good;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
