package org.example.Gift;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class Gift {
    private long giverId;
    private String giverName;
    private int total;
    private int remaining;
    private Map<Long, Integer> takers = new LinkedHashMap<>(); // takerId → qancha olgan

    public Gift(long giverId, String giverName, int total) {
        this.giverId = giverId;
        this.giverName = giverName;
        this.total = total;
        this.remaining = total;
    }

    public boolean hasTaken(long takerId) {
        return takers.containsKey(takerId);
    }

    public boolean take(long takerId) {
        if (remaining <= 0 || takers.containsKey(takerId)) {
            return false;
        }
        takers.put(takerId, 1);
        remaining--;
        return true;
    }

    public int getRemaining() {
        return remaining;
    }

    public Map<String, Integer> getWinners(Function<Long, String> nameResolver) {
        Map<String, Integer> winners = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : takers.entrySet()) {
            winners.put(nameResolver.apply(entry.getKey()), entry.getValue());
        }
        return winners;
    }

    public String getGiverName() {
        return giverName;
    }
}
