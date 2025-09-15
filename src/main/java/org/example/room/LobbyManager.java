package org.example.room;

import java.util.*;

public class LobbyManager {
    private final Map<Long, GameLobby> activeLobbies = new HashMap<>();
    private final Set<Integer> usedIds = new HashSet<>();
    private final Random random = new Random();

    public boolean createLobby(long chatId) {
        if (activeLobbies.containsKey(chatId)) {
            return false;
        }

        int gameId = generateUniqueId();
        GameLobby lobby = new GameLobby(chatId, gameId);
        activeLobbies.put(chatId, lobby);
        return true;
    }

    public GameLobby getLobby(long chatId) {
        return activeLobbies.get(chatId);
    }

    public void removeLobby(long chatId) {
        activeLobbies.remove(chatId);
    }

    public GameLobby getLobbyByPlayer(long playerId) {
        for (GameLobby lobby : activeLobbies.values()) {
            if (lobby.getAllPlayers().contains(playerId)) {
                return lobby;
            }
        }
        return null;
    }

    private int generateUniqueId() {
        if (usedIds.size() >= 90000) {
            usedIds.clear();
            System.out.println("♻️ Barcha ID lar ishlatildi. Map tozalandi, boshidan yaratish boshlandi.");
        }

        int id;
        do {
            id = 10000 + random.nextInt(90000); // 10000–99999 orasida
        } while (usedIds.contains(id));

        usedIds.add(id);
        return id;
    }
}
