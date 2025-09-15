package org.example.bot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MafiaBot());
            System.out.println("Bot ishga tushdi.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}