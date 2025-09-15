package org.example.shop;

import java.util.Map;

public class Item {
    private static final Map<String, String> ITEM_EMOJIS = Map.of(
            "Statistikani tiklash", "🔄",
            "Hujjatlar", "📁",
            "Himoya", "🛡",
            "Maska", "🎭",
            "Miltiq", "🔫",
            "Qotildan himoya", "🛑",
            "Ovoz berishni himoya qilish", "⚖️"
    );
    private static final Map<String, String> NAME_TO_CODE = Map.of(
            "Himoya", "shield",
            "Qotildan himoya", "killershield",
            "Ovoz berishni himoya qilish", "voteshield",
            "Miltiq", "gun",
            "Maska", "mask",
            "Hujjatlar", "fakedoc",
            "Statistikani tiklash", "reset"
    );

    private final String name;
    private final String price;
    private final String currency;

    public Item(String name, String price, String currency) {
        this.name = name;
        this.price = price;
        this.currency = currency;
    }

    public String toButtonText() {
        String emoji = getEmojiForItem(name);
        return emoji + " " + name + " - " + currency + " " + price;
    }

    public String toCallbackData() {
        String code = NAME_TO_CODE.getOrDefault(name, name.replace(" ", "").toLowerCase());
        return "by_" + code + "_" + price + "_" + currency;
    }

    public static String getEmojiForItem(String name) {
        return ITEM_EMOJIS.getOrDefault(name, "❓");
    }
}
