package org.example.room;

import lombok.Getter;
import lombok.Setter;
import org.example.roles.Role;

import java.util.*;

public class GameLobby {
    @Getter
    private final long chatId;
    @Getter
    private final long gameId;
    @Getter
    private final long startTime;

    @Getter
    private final Set<Long> players = new HashSet<>();
    @Getter
    private final Map<Long, Role> playerRoles = new HashMap<>();
    private final Set<Long> alivePlayers = new HashSet<>();
    private final Map<String, Long> playerNameToIdMap = new HashMap<>();
    private final Map<Long, Boolean> skippedVotes = new HashMap<>();
    private final Set<Long> suicidedPlayers = new HashSet<>();



    public GameLobby(long chatId, long gameId) {
        this.chatId = chatId;
        this.gameId = gameId;
        this.startTime = System.currentTimeMillis();
    }

    public void changePlayerRole(Long playerId, Role newRole) {
        if (playerRoles.containsKey(playerId)) {
            playerRoles.put(playerId, newRole);
            System.out.println("🔄 " + playerId + " roli " + newRole.getDisplayName() + " ga o‘zgartirildi");
        } else {
            System.out.println("⚠️ " + playerId + " o‘yinchi roli mavjud emas, shuning uchun o‘zgartirib bo‘lmadi!");
        }
    }

    public Set<Long> getAllPlayers() {
        return new HashSet<>(players);
    }

    public boolean addPlayer(long userId, String playerName) {
        boolean added = players.add(userId);
        if (added) {
            alivePlayers.add(userId);
            playerNameToIdMap.put(playerName, userId);
        }
        return added;
    }

    public Long getPlayerIdByName(String name) {
        return playerNameToIdMap.get(name);
    }

    @Getter
    @Setter
    private int messageId;

    public void setPlayerRoles(Map<Long, Role> roles) {
        playerRoles.clear();
        playerRoles.putAll(roles);
    }

    public String getPlayerRole(Long playerId) {
        Role role = playerRoles.get(playerId);
        return role != null ? role.getDisplayName() : null;
    }

    public Set<Long> getAlivePlayers() {
        return new HashSet<>(alivePlayers);
    }

    public boolean isAlive(Long playerId) {
        return alivePlayers.contains(playerId);
    }

    public boolean isInGame(Long playerId) {
        return players.contains(playerId);
    }

    public void killPlayer(Long playerId) {
        alivePlayers.remove(playerId);
    }

    public List<Long> getPlayersByRole(Role role) {
        List<Long> list = new ArrayList<>();
        for (Map.Entry<Long, Role> entry : playerRoles.entrySet()) {
            if (entry.getValue() == role) {
                list.add(entry.getKey());
            }
        }
        return list;
    }
    private final Set<Long> sleepingPlayers = new HashSet<>();
    private final Set<Long> wakeUpNextNight = new HashSet<>();

    public void setSleeping(Long playerId) {
        sleepingPlayers.add(playerId);
        wakeUpNextNight.add(playerId);

    }
    public void skipVote(Long userId) {
        if (!alivePlayers.contains(userId)) return; // faqat tirik o'yinchilar
        skippedVotes.put(userId, true);
        System.out.println("⚠️ O'yinchi " + userId + " ovoz berishni otkazdi.");
    }

    public boolean isSleeping(Long playerId) {
        return sleepingPlayers.contains(playerId);
    }

    public void wakeUpPlayers() {
        for (Long playerId : wakeUpNextNight) {
            sleepingPlayers.remove(playerId);
            System.out.println("uygotildi");
        }
        wakeUpNextNight.clear();
    }

    private final Set<Long> protectedPlayers = new HashSet<>();

    public void protectPlayer(Long playerId) {
        protectedPlayers.add(playerId);
    }

    public boolean isProtected(Long playerId) {
        return protectedPlayers.contains(playerId);
    }

    public void clearProtections() {
        protectedPlayers.clear(); // har tun boshida tozalanadi
    }

    @Getter
    @Setter
    private boolean extended = false;

    @Getter
    @Setter
    private boolean forceStart = false;

    public boolean isSuicided(Long playerId) {
        return suicidedPlayers.contains(playerId);
    }

    public void addSuicidedPlayer(Long playerId) {
        suicidedPlayers.add(playerId);
        System.out.println("💀 " + playerId + " kunduzi o‘zini osdi (suicid).");
    }
    @Getter
    @Setter
    private String currentStage = "Ro'yxatdan o'tish";

    public void startGame() {
        this.currentStage = "Tun";
    }
}
