package org.example.roles;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RoleShuffler {
    public static void smartPlayerShuffle(List<Long> players) {
        Random random = new Random();
        for (int i = players.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(players, i, j);
        }
        for (int i = 0; i < players.size() - 2; i += 3) {
            if (random.nextInt(100) < 20) {
                Collections.swap(players, i, i + 1);
            }
        }
    }
}
