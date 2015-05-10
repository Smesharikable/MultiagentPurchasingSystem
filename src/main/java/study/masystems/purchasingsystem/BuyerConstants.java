package study.masystems.purchasingsystem;

public class BuyerConstants {
    private long customerPeriod;
    private long customerRepPeriod;
    private long checkNeedsPeriod;
    private long activityPeriod;
    private int searchCustomerIteration;

    public int getSearchCustomerIteration() {
        return searchCustomerIteration;
    }

    public void setSearchCustomerIteration(int searchCustomerIteration) {
        this.searchCustomerIteration = searchCustomerIteration;
    }

    public long getActivityPeriod() {
        return activityPeriod;
    }

    public void setActivityPeriod(long activityPeriod) {
        this.activityPeriod = activityPeriod;
    }

    public long getCheckNeedsPeriod() {
        return checkNeedsPeriod;
    }

    public void setCheckNeedsPeriod(long checkNeedsPeriod) {
        this.checkNeedsPeriod = checkNeedsPeriod;
    }

    public long getCustomerRepPeriod() {
        return customerRepPeriod;
    }

    public void setCustomerRepPeriod(long customerRepPeriod) {
        this.customerRepPeriod = customerRepPeriod;
    }

    public long getCustomerPeriod() {
        return customerPeriod;
    }

    public void setCustomerPeriod(long customerPeriod) {
        this.customerPeriod = customerPeriod;
    }


}
