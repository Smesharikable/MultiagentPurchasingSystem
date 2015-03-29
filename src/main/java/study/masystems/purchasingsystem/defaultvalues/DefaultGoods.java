package study.masystems.purchasingsystem.defaultvalues;

import javafx.util.Pair;
import study.masystems.purchasingsystem.GoodInfo;

import java.util.*;

/**
 * Generate random default table of goods.
 */
public class DefaultGoods {

    private static String[] goods = new String[] {
        "pajamas", "suit", "socks", "skirt", "T-shirt", "purse", "jeans"
    };

    private static int deliveryTimeLow = 1;
    private static int deliveryTimeHigh = 14;

    private static double costLow = 300;
    private static double costHigh = 10000;

    private static int minQuantityLow = 10;
    private static int minQuantityHigh = 40;

    private static final Random random = new Random(System.nanoTime());

    public DefaultGoods() {
    }

    public static HashMap<String, GoodInfo> getRandomGoodsTable() {
        HashMap<String, GoodInfo> goodsTable = new HashMap<String, GoodInfo>();

        List<String> selectedGoods = getRandomGoods();
        int deliveryTime = getRandomDeliveryTime();

        for (String good: selectedGoods) {
            int minQuantity = randInt(minQuantityLow, minQuantityHigh);
            double price = randDouble(costLow, costHigh);
            goodsTable.put(good, new GoodInfo(minQuantity, price, deliveryTime));
        }

        return goodsTable;
    }

    public static List<String> getRandomGoods() {
        int goodsExclude = random.nextInt(DefaultGoods.goods.length);
        List<String> selectedGoods = new ArrayList<String>(Arrays.asList(goods));

        for (int i = 0; i < goodsExclude; i++) {
            selectedGoods.remove(random.nextInt(selectedGoods.size()));
        }

        return selectedGoods;
    }

    public static int getRandomDeliveryTime() {
        return randInt(deliveryTimeLow, deliveryTimeHigh);
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
