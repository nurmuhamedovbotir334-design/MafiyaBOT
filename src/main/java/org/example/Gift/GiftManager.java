package org.example.Gift;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GiftManager {
    // giftlarni chatId + messageId bo‘yicha saqlaymiz
    private static final Map<String, Gift> gifts = new ConcurrentHashMap<>();

    private static String key(long chatId, int messageId) {
        return chatId + ":" + messageId;
    }

    public static void addGift(long chatId, int messageId, Gift gift) {
        gifts.put(key(chatId, messageId), gift);
    }

    public static Gift getGift(long chatId, int messageId) {
        return gifts.get(key(chatId, messageId));
    }

    public static void removeGift(long chatId, int messageId) {
        gifts.remove(key(chatId, messageId));
    }
}
