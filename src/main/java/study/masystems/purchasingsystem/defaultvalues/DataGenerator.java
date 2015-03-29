package study.masystems.purchasingsystem.defaultvalues;

import study.masystems.purchasingsystem.GoodInfo;
import study.masystems.purchasingsystem.GoodNeed;

import java.util.*;

/**
 * Generate random default table of goods.
 */
public class DataGenerator {

    private static String[] goods = new String[] {
        "pajamas", "suit", "socks", "skirt", "T-shirt", "purse", "jeans"
    };

    private static int deliveryTimeMin = 0;
    private static int deliveryTimeMax = 14;

    private static double costMin = 300;
    private static double costMax = 10000;

    private static int purchaseQuantityMin = 10;
    private static int purchaseQuantityMax = 40;

    private static int goodQuantityMin = 1;
    private static int goodQuantityMax = 20;

    private static double moneyAmountMin = 600;
    private static double moneyAmountMax = 10000;

    private static final Random random = new Random(System.nanoTime());

    public DataGenerator() {
    }

    public static HashMap<String, GoodInfo> getRandomGoodsTable() {
        HashMap<String, GoodInfo> goodsTable = new HashMap<String, GoodInfo>();

        List<String> selectedGoods = getRandomGoods();
        int deliveryTime = getRandomDeliveryTime();

        for (String good: selectedGoods) {
            int minQuantity = getRandomPurchaseQuantity();
            double price = getRandomCost();
            goodsTable.put(good, new GoodInfo(price, minQuantity, deliveryTime));
        }

        return goodsTable;
    }

    public static List<GoodNeed> getRandomGoodNeeds() {
        ArrayList<GoodNeed> goodNeeds = new ArrayList<GoodNeed>();
        List<String> selectedGoods = getRandomGoods();
        for (String s : selectedGoods) {
            int quantity = getRandomGoodQuantity();
            int period = getRandomDeliveryTime();
            goodNeeds.add(new GoodNeed(s, quantity, period));
        }
        return goodNeeds;
    }

    public static List<String> getRandomGoods() {
        int goodsExclude = random.nextInt(DataGenerator.goods.length);
        List<String> selectedGoods = new ArrayList<String>(Arrays.asList(goods));

        for (int i = 0; i < goodsExclude; i++) {
            selectedGoods.remove(random.nextInt(selectedGoods.size()));
        }

        return selectedGoods;
    }

    public static double getRandomCost() {
        return randDouble(costMin, costMax);
    }

    public static int getRandomDeliveryTime() {
        return randInt(deliveryTimeMin, deliveryTimeMax);
    }

    public static int getRandomPurchaseQuantity() {
        return randInt(purchaseQuantityMin, purchaseQuantityMax);
    }

    public static double getRandomMoneyAmount() {
        return randDouble(moneyAmountMin, moneyAmountMax);
    }

    public static int getRandomGoodQuantity() {
        return randInt(goodQuantityMin, goodQuantityMax);
    }

    public static int randInt(int min, int max) {
        return random.nextInt((max - min) + 1) + min;
    }

    public static long randLong(long min, long max) {
        return Math.max(Math.floorMod(random.nextLong(), max), min);
    }

    public static double randDouble(double min, double max) {
        return random.nextDouble() * (max - min) + min;
    }
}
