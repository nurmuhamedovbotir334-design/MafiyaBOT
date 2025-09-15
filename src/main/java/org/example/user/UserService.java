package org.example.user;

import java.sql.*;
import java.util.concurrent.*;

public class UserService {
    public static final String DB_URL = "jdbc:postgresql://yamabiko.proxy.rlwy.net:55139/railway";
    public static final String USER = "postgres";
    public static final String PASSWORD = "mLiSAWrPKDwZQfojNppbvYQtHGQIsBWj";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    // ✅ Muddati tugagan Pro foydalanuvchilardan olib tashlash
    public static void checkAndRemoveExpiredPro() {
        String sql = "UPDATE userss SET is_pro = FALSE WHERE pro_expiry IS NOT NULL AND pro_expiry < NOW() AND is_pro = TRUE";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            PreparedStatement stmt = connection.prepareStatement(sql);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("⏰ " + rows + " ta foydalanuvchidan Pro olib tashlandi (muddati tugagan).");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void startProExpiryChecker() {
        scheduler.scheduleAtFixedRate(UserService::checkAndRemoveExpiredPro, 0, 1, TimeUnit.DAYS);
    }
}
