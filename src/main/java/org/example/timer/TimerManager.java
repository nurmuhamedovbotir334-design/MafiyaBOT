package org.example.timer;

import java.sql.*;
import java.util.*;

public class TimerManager {
    // 🔹 Default vaqt sozlamalari
    private static final Map<String, Integer> DEFAULT_STAGES = new HashMap<>();
    static {
        DEFAULT_STAGES.put("Vote", 30);
        DEFAULT_STAGES.put("So'ngi sozni aytish vaqti", 60);
        DEFAULT_STAGES.put("Tasdiqlash", 25);
        DEFAULT_STAGES.put("Kun", 25);
        DEFAULT_STAGES.put("Afsungar", 60);
        DEFAULT_STAGES.put("Tun", 25);
        DEFAULT_STAGES.put("Ro'yxatdan o'tish", 240);
    }

    // 🔹 Timerlar
    private static final Map<Long, Timer> timers = new HashMap<>();
    private static final Map<Long, Long> currentDelays = new HashMap<>();
    private static final Map<Long, Runnable> callbacks = new HashMap<>();

    public static final String DB_URL = "jdbc:postgresql://yamabiko.proxy.rlwy.net:55139/railway";
    public static final String USER = "postgres";
    public static final String PASSWORD = "mLiSAWrPKDwZQfojNppbvYQtHGQIsBWj";

    private static Connection connection;

    static {
        try {
            connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
            System.out.println("✅ DB ga ulanish muvaffaqiyatli!");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("❌ DB ga ulanishda xatolik!");
        }
    }
    public static void cancelTimerForGroup(long groupId) {
        Timer timer = timers.get(groupId);
        if (timer != null) {
            timer.cancel();
            timers.remove(groupId);
            currentDelays.remove(groupId);
            callbacks.remove(groupId);
            System.out.println("⏹️ Guruh uchun timer bekor qilindi: " + groupId);
        } else {
            System.out.println("⚠️ Guruh uchun aktiv timer topilmadi: " + groupId);
        }
    }
    private int getStageDurationForGroup(long groupId, String stage) {
        try {
            try (PreparedStatement checkStmt = connection.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM group_stage_times WHERE group_id = ?")) {
                checkStmt.setLong(1, groupId);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt("cnt") == 0) {
                    try (PreparedStatement insertStmt = connection.prepareStatement(
                            "INSERT INTO group_stage_times (group_id, stage_name, duration_seconds) VALUES (?, ?, ?)")) {
                        for (Map.Entry<String, Integer> entry : DEFAULT_STAGES.entrySet()) {
                            insertStmt.setLong(1, groupId);
                            insertStmt.setString(2, entry.getKey());
                            insertStmt.setInt(3, entry.getValue());
                            insertStmt.addBatch();
                        }
                        insertStmt.executeBatch();
                        System.out.println("➕ DB ga default vaqtlar qo‘shildi: Group " + groupId);
                    }
                }
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT duration_seconds FROM group_stage_times WHERE group_id = ? AND stage_name = ?")) {
                stmt.setLong(1, groupId);
                stmt.setString(2, stage);

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("duration_seconds");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return DEFAULT_STAGES.getOrDefault(stage, 30); // fallback
    }

    public void startTimerForGroup(long groupId, String stage, Runnable onTimeEnd) {
        int seconds = getStageDurationForGroup(groupId, stage);

        System.out.println("Guruh: " + groupId + " | Bosqich: " + stage + " | Sekund: " + seconds);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                onTimeEnd.run();
                timers.remove(groupId);
                currentDelays.remove(groupId);
                callbacks.remove(groupId);
                timer.cancel();
            }
        }, seconds * 1000L);

        timers.put(groupId, timer);
        currentDelays.put(groupId, (long) seconds * 1000);
        callbacks.put(groupId, onTimeEnd);
    }
    public void extendTimer(Long groupId, int multiplier) {
        Timer timer = timers.get(groupId);
        if (timer != null) {
            timer.cancel();

            long newDelay = currentDelays.get(groupId) * multiplier;
            Runnable callback = callbacks.get(groupId);

            Timer newTimer = new Timer();
            newTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    callback.run();
                    timers.remove(groupId);
                    currentDelays.remove(groupId);
                    callbacks.remove(groupId);
                }
            }, newDelay);

            timers.put(groupId, newTimer);
            currentDelays.put(groupId, newDelay);
        }
    }
}
