package study.masystems.purchasingsystem;

public class CustomerConstants {
    private int purchaseNumberLimit;
    private int waitForSuppliresLimit;
    private long supTimeout;
    private long supPropTimeout;
    private long supAgreeTimeout;
    private long purchaseTimeout;

    public int getPurchaseNumberLimit() {
        return purchaseNumberLimit;
    }

    public void setPurchaseNumberLimit(int purchaseNumberLimit) {
        this.purchaseNumberLimit = purchaseNumberLimit;
    }

    public int getWaitForSuppliresLimit() {
        return waitForSuppliresLimit;
    }

    public void setWaitForSuppliresLimit(int waitForSupliresLimit) {
        this.waitForSuppliresLimit = waitForSupliresLimit;
    }

    public long getSupTimeout() {
        return supTimeout;
    }

    public void setSupTimeout(long supTimeout) {
        this.supTimeout = supTimeout;
    }

    public long getSupPropTimeout() {
        return supPropTimeout;
    }

    public void setSupPropTimeout(long supPropTimeout) {
        this.supPropTimeout = supPropTimeout;
    }

    public long getSupAgreeTimeout() {
        return supAgreeTimeout;
    }

    public void setSupAgreeTimeout(long supAgreeTimeout) {
        this.supAgreeTimeout = supAgreeTimeout;
    }

    public long getPurchaseTimeout() {
        return purchaseTimeout;
    }

    public void setPurchaseTimeout(long purchaseTimeout) {
        this.purchaseTimeout = purchaseTimeout;
    }
}