package org.example.roles;

import java.util.*;

public class RoleAssigner {
    public static Map<Long, Role> assignRoles(Set<Long> players) {
        List<Long> playerList = new ArrayList<>(players);
        RoleShuffler.smartPlayerShuffle(playerList);
        int playerCount = playerList.size();
        System.out.println("Total players: " + playerCount);
        List<Role> assignedRoles = generateRolesByPlayerCount(playerCount);
        System.out.println("Generated roles count: " + assignedRoles.size());
        if (assignedRoles.size() < playerCount) {
            int citizensToAdd = playerCount - assignedRoles.size();
            System.out.println("Adding " + citizensToAdd + " CITIZEN roles");
            while (assignedRoles.size() < playerCount) {
                assignedRoles.add(Role.CITIZEN);
            }
        }
        Collections.shuffle(assignedRoles);
        System.out.println("Final roles count: " + assignedRoles.size());
        Map<Long, Role> result = new HashMap<>();
        for (int i = 0; i < playerCount; i++) {
            result.put(playerList.get(i), assignedRoles.get(i));
        }
        System.out.println("Assigned roles to " + result.size() + " players");
        return result;
    }

    private static List<Role> generateRolesByPlayerCount(int count) {
        List<Role> roles = new ArrayList<>();
        roles.add(Role.SHERIF);
        roles.add(Role.DOCTOR);
        roles.add(Role.DON);
        if (count >= 5) roles.add(Role.KILLER);
        if (count >= 6) roles.add(Role.CITIZEN);
        if (count >= 7) roles.add(Role.MAFIA);
        if (count >= 8) roles.add(Role.SLEEPWALKER);
        if (count >= 9) roles.add(Role.GUARD);
        if (count >= 10) roles.add(Role.CITIZEN);
        if (count >= 11) roles.add(Role.FOX);
        if (count >= 12) {
            roles.add(Role.SERGEANT);
        }
        if (count >= 13) roles.add(Role.WOLF);
        if (count >= 14) roles.add(Role.KING);
        if (count == 15) {
            roles.add(Role.CITIZEN);
        } else if (count >= 17) {
            roles.add(Role.BIG_BRO);
            roles.add(Role.LITTLE_BRO);
        }
        if (count >= 18) roles.add(Role.LEADER);
        if (count >= 19) {
            roles.add(Role.MAFIA);
        }
        if (count >= 20) roles.add(Role.KILLER);
        if (count >= 22) {
            roles.add(Role.MAFIA);
        }
        if (count >= 23) {
            roles.add(Role.GENTLEMAN);
        }
        while (roles.size() < count && roles.size() < 30) {
            roles.add(Role.CITIZEN);
        }
        return roles;
    }
    //impostor, advokat, sehrgar, darbadar, afsungar yoq
}