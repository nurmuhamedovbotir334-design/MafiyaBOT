package org.example.dtabase;

import org.example.user.UserStats;

import java.sql.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Database {
    public static final String DB_URL = "jdbc:postgresql://yamabiko.proxy.rlwy.net:55139/railway";
    public static final String USER = "postgres";
    public static final String PASSWORD = "mLiSAWrPKDwZQfojNppbvYQtHGQIsBWj";

    private static long chatId;

    public static void updateProStatus(Long chatId, int days) {
        String checkSql = "SELECT pro_expiry, is_pro FROM userss WHERE chat_id = ?";
        String updateSql = "UPDATE userss SET pro_expiry = NOW() + (? * INTERVAL '1 day'), is_pro = TRUE WHERE chat_id = ?";
        String insertSql = "INSERT INTO userss (chat_id, pro_expiry, is_pro, created_at) VALUES (?, NOW() + (? * INTERVAL '1 day'), TRUE, NOW())";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            PreparedStatement checkStmt = connection.prepareStatement(checkSql);
            checkStmt.setLong(1, chatId);
            ResultSet rs = checkStmt.executeQuery();

            java.sql.Timestamp newExpiry;
            if (rs.next()) {
                java.sql.Timestamp currentExpiry = rs.getTimestamp("pro_expiry");
                boolean isPro = rs.getBoolean("is_pro");

                if (currentExpiry != null && currentExpiry.after(new java.sql.Timestamp(System.currentTimeMillis())) && isPro) {
                    // agar pro muddati hali tugamagan bo‘lsa, ustiga qo‘shiladi
                    newExpiry = new java.sql.Timestamp(currentExpiry.getTime() + (long) days * 24 * 60 * 60 * 1000);
                } else {
                    // agar pro muddati tugagan yoki yo‘q bo‘lsa, hozirgi vaqtdan boshlanadi
                    newExpiry = new java.sql.Timestamp(System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000);
                }

                PreparedStatement updateStmt = connection.prepareStatement(updateSql);
                updateStmt.setInt(1, days);
                updateStmt.setLong(2, chatId);
                updateStmt.executeUpdate();

                System.out.println("Pro status updated for chatId: " + chatId + " for " + days + " days (new expiry: " + newExpiry + ")");
            } else {
                PreparedStatement insertStmt = connection.prepareStatement(insertSql);
                insertStmt.setLong(1, chatId);
                insertStmt.setInt(2, days);
                insertStmt.executeUpdate();

                System.out.println("New user added with Pro status for chatId: " + chatId + " for " + days + " days");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public static boolean isRegistered(long chatId) {
        String sql = "SELECT COUNT(*) FROM userss WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int count = rs.getInt(1);
                return count > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    public static void saveUser(long chatId, String firstName, String gender) {
        String insertUserSql = "INSERT INTO userss (chat_id, first_name, gender) VALUES (?, ?, ?)";
        String insertItemSql = "INSERT INTO user_items (chat_id, item_name, is_active, quantity) VALUES (?, ?, false, 0)";

        String[] items = {
                "shield",         // 🛡 Himoya
                "killershield",   // ⛑️ Qotildan himoya
                "voteshield",     // ⚖️ Ovoz berishni himoya qilish
                "gun",            // 🔫 Miltiq
                "mask",           // 🎭 Maska
                "fakedoc",        // 📁 Soxta hujjat
                "nextrole"        // 🃏 Keyingi o'yindagi rolingiz (bu bo'lishi mumkin quantity emas, balki matn keyinchalik)
        };

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            try (PreparedStatement userStmt = connection.prepareStatement(insertUserSql)) {
                userStmt.setLong(1, chatId);
                userStmt.setString(2, firstName);
                userStmt.setString(3, gender);
                userStmt.executeUpdate();
            }
            try (PreparedStatement itemStmt = connection.prepareStatement(insertItemSql)) {
                for (String item : items) {
                    itemStmt.setLong(1, chatId);
                    itemStmt.setString(2, item);
                    itemStmt.addBatch();
                }
                itemStmt.executeBatch();
            }

            System.out.println("User and items saved successfully");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static UserStats getUserStats(long chatId) {
        UserStats stats = new UserStats();

        String userSql = "SELECT chat_id, first_name, gender, dollar, olmos, is_pro, win_count, play_count FROM userss WHERE chat_id = ?";
        String itemsSql = "SELECT item_name, quantity, is_active FROM user_items WHERE chat_id = ?";
        String roleSql = "SELECT role FROM user_role WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // 1. userss jadvalidan malumot
            try (PreparedStatement userStmt = conn.prepareStatement(userSql)) {
                userStmt.setLong(1, chatId);
                ResultSet rs = userStmt.executeQuery();
                if (rs.next()) {
                    stats.setChatId(rs.getLong("chat_id"));
                    stats.setFirstName(rs.getString("first_name"));
                    stats.setDollar(rs.getInt("dollar"));
                    stats.setDiamond(rs.getInt("olmos"));
                    stats.set_Pro(rs.getBoolean("is_pro"));
                    stats.setWin((int) rs.getLong("win_count"));
                    stats.setPlayCount((int) rs.getLong("play_count"));
                }
            }
            try (PreparedStatement itemsStmt = conn.prepareStatement(itemsSql)) {
                itemsStmt.setLong(1, chatId);
                ResultSet rs = itemsStmt.executeQuery();
                while (rs.next()) {
                    String itemName = rs.getString("item_name");
                    int quantity = rs.getInt("quantity");
                    boolean isActive = rs.getBoolean("is_active");

                    switch (itemName.toLowerCase()) {
                        case "shield" -> {
                            stats.setShield(quantity);
                            stats.setShieldActive(isActive);
                        }
                        case "killershield" -> {
                            stats.setMurderProtect(quantity);
                            stats.setMurderProtectActive(isActive);
                        }
                        case "voteshield" -> {
                            stats.setVoteProtect(quantity);
                            stats.setVoteProtectActive(isActive);
                        }
                        case "gun" -> {
                            stats.setGun(quantity);
                            stats.setGunActive(isActive);
                        }
                        case "mask" -> {
                            stats.setMask(quantity);
                            stats.setMaskActive(isActive);
                        }
                        case "fakedoc" -> {
                            stats.setFakeDoc(quantity);
                            stats.setFakeDocActive(isActive);
                        }
                    }
                }
            }

            try (PreparedStatement roleStmt = conn.prepareStatement(roleSql)) {
                roleStmt.setLong(1, chatId);
                ResultSet rs = roleStmt.executeQuery();
                if (rs.next()) {
                    stats.setRole(rs.getString("nextrole"));
                } else {
                    stats.setRole("-");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return stats;
    }

    public static void toggleItemActiveStatus(long chatId, String itemName) {
        String query = "UPDATE user_items SET is_active = NOT is_active WHERE chat_id = ? AND item_name = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, chatId);
            stmt.setString(2, itemName);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                System.out.println("Hech qanday malumot o'zgarmadi (topilmadi).");
            } else {
                System.out.println(itemName + "holati muvaffaqiyatli o'zgartirildi.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public static void decrementItemQuantity(long chatId, String itemName) {
        String query = "UPDATE user_items SET quantity = quantity - 1 " +
                "WHERE chat_id = ? AND item_name = ? AND is_active = TRUE AND quantity > 0";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, chatId);
            stmt.setString(2, itemName);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println(itemName + " quantity 1 taga kamaytirildi.");
            } else {
                System.out.println(itemName + " kamaytirilmadi (topilmadi yoki 0).");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Integer getActiveItemQuantity(long chatId, String itemName) {
        String query = "SELECT quantity FROM user_items " +
                "WHERE chat_id = ? AND item_name = ? AND is_active = TRUE AND quantity > 0 LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, chatId);
            stmt.setString(2, itemName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantity");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null; // hech narsa topilmasa yoki shartlarga tushmasa
    }

    public static int getDiamondCount(long chatId) {
        int diamondCount = 0;
        String query = "SELECT olmos FROM userss WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                diamondCount = resultSet.getInt("olmos");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return diamondCount;
    }

    public static int getDollarByChatId(long chatId) {
        String query = "SELECT dollar FROM userss WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                return resultSet.getInt("dollar");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static void decreaseDiamond(long chatId, int son) {
        String selectQuery = "SELECT olmos FROM userss WHERE chat_id = ?";
        String updateQuery = "UPDATE userss SET olmos = ? WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement selectStatement = connection.prepareStatement(selectQuery)) {

            selectStatement.setLong(1, chatId);
            ResultSet resultSet = selectStatement.executeQuery();

            if (resultSet.next()) {
                int currentOlmos = resultSet.getInt("olmos");
                int newOlmos = Math.max(0, currentOlmos - son); // 0 dan pastga tushmasin

                try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                    updateStatement.setInt(1, newOlmos);
                    updateStatement.setLong(2, chatId);
                    updateStatement.executeUpdate();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void decreaseDollar(long chatId, int son) {
        String selectQuery = "SELECT dollar FROM userss WHERE chat_id = ?";
        String updateQuery = "UPDATE userss SET dollar = ? WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement selectStatement = connection.prepareStatement(selectQuery)) {

            selectStatement.setLong(1, chatId);
            ResultSet resultSet = selectStatement.executeQuery();

            if (resultSet.next()) {
                int currentDollar = resultSet.getInt("dollar");
                int newDollar = Math.max(0, currentDollar - son); // 0 dan pastga tushmasin

                try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                    updateStatement.setInt(1, newDollar);
                    updateStatement.setLong(2, chatId);
                    updateStatement.executeUpdate();
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void resetGameStats(long chatId) {
        String updateQuery = "UPDATE userss SET win_count = 0, play_count = 0 WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {

            updateStatement.setLong(1, chatId);
            updateStatement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void decreaseCurrencyAndAddItem(long chatId, int son, String itemName, String type) {
        String selectCurrencyQuery;
        String updateCurrencyQuery;

        if (type.equalsIgnoreCase("dollar")) {
            selectCurrencyQuery = "SELECT dollar FROM userss WHERE chat_id = ?";
            updateCurrencyQuery = "UPDATE userss SET dollar = ? WHERE chat_id = ?";
        } else {
            selectCurrencyQuery = "SELECT olmos FROM userss WHERE chat_id = ?";
            updateCurrencyQuery = "UPDATE userss SET olmos = ? WHERE chat_id = ?";
        }

        String selectItemQuery = "SELECT quantity FROM user_items WHERE chat_id = ? AND item_name = ?";
        String updateItemQuery = "UPDATE user_items SET quantity = quantity + 1 WHERE chat_id = ? AND item_name = ?";
        String insertItemQuery = "INSERT INTO user_items (chat_id, item_name, quantity) VALUES (?, ?, 1)";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
            // 1. Dollar yoki Olmosni kamaytirish
            try (PreparedStatement selectStmt = connection.prepareStatement(selectCurrencyQuery)) {
                selectStmt.setLong(1, chatId);
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    int currentVal = rs.getInt(1);
                    int newVal = currentVal - son;
                    if (newVal < 0) newVal = 0;

                    try (PreparedStatement updateStmt = connection.prepareStatement(updateCurrencyQuery)) {
                        updateStmt.setInt(1, newVal);
                        updateStmt.setLong(2, chatId);
                        updateStmt.executeUpdate();
                    }
                }
            }

            try (PreparedStatement selectItemStmt = connection.prepareStatement(selectItemQuery)) {
                selectItemStmt.setLong(1, chatId);
                selectItemStmt.setString(2, itemName);
                ResultSet rsItem = selectItemStmt.executeQuery();

                if (rsItem.next()) {
                    try (PreparedStatement updateItemStmt = connection.prepareStatement(updateItemQuery)) {
                        updateItemStmt.setLong(1, chatId);
                        updateItemStmt.setString(2, itemName);
                        updateItemStmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertItemStmt = connection.prepareStatement(insertItemQuery)) {
                        insertItemStmt.setLong(1, chatId);
                        insertItemStmt.setString(2, itemName);
                        insertItemStmt.executeUpdate();
                    }
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void convertDiamondToDollar(long chatId, int dollar, int olmos) {

        String selectQuery = "SELECT olmos, dollar FROM userss WHERE chat_id = ?";
        String updateQuery = "UPDATE userss SET olmos = ?, dollar = ? WHERE chat_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);) {
            try (PreparedStatement selectStmt = conn.prepareStatement(selectQuery)) {
                selectStmt.setLong(1, chatId);
                ResultSet rs = selectStmt.executeQuery();

                if (rs.next()) {
                    int currentDiamonds = rs.getInt("olmos");
                    int currentDollars = rs.getInt("dollar");

                    if (currentDiamonds >= olmos) {
                        int newDiamonds = currentDiamonds - olmos;
                        int newDollars = currentDollars + dollar;

                        // Yangi qiymatlar bilan yangilaymiz
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                            updateStmt.setInt(1, newDiamonds);
                            updateStmt.setInt(2, newDollars);
                            updateStmt.setLong(3, chatId);
                            updateStmt.executeUpdate();
                            System.out.println("Muvaffaqiyatli yangilandi.");
                        }
                    } else {
                        System.out.println("Yetarli olmos mavjud emas.");
                    }
                } else {
                    System.out.println("Foydalanuvchi topilmadi.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isUserPro(long chatId) throws SQLException {
        String sql = "SELECT is_pro FROM userss WHERE chat_id = ?";
        Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql); {
                statement.setLong(1, chatId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("is_pro");
                }
            }
        }
        return false;
    }

    public static double getBalance(long chatId) {
        Database.chatId = chatId;
        String query = "SELECT  olmos FROM userss WHERE chat_id = ?";
        double balance = 0;

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                balance = resultSet.getInt("olmos");
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return balance;
    }

    public static void updateBalance(long chatId, double olmos) {
        String query = "UPDATE userss SET olmos = olmos + ? WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, (int) olmos);  // qo‘shiladigan qiymat
            statement.setLong(2, chatId);      // qaysi userga
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // Dollar va Olmos qo‘shish
    public static void addBalance(long chatId, int dollar, int olmos) {
        String query = "UPDATE userss SET dollar = dollar + ?, olmos = olmos + ? WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, dollar);
            statement.setInt(2, olmos);
            statement.setLong(3, chatId);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // O‘yinlar sonini oshirish
    public static void incrementGames(long chatId) {
        String query = "UPDATE userss SET play_count = play_count + 1 WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 🔹 Balansni aniq qiymatga o‘rnatish (ustiga qo‘shmasdan)
    public static void updateBalanceALMZ(long chatId, double newOlmos) {
        String query = "UPDATE userss SET olmos = ? WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, (int) newOlmos);  // yangi balans qiymati
            statement.setLong(2, chatId);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 🔹 Qo‘shimcha balans qo‘shish (masalan oluvchi odamga berish)
    public static void addBalanceALMZ(long chatId, int dollar, int olmos) {
        String query = "UPDATE userss SET dollar = dollar + ?, olmos = olmos + ? WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setInt(1, dollar);
            statement.setInt(2, olmos);
            statement.setLong(3, chatId);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // G‘alabalar sonini oshirish
    public static void incrementWins(long chatId) {
        String query = "UPDATE userss SET win_count = win_count + 1 WHERE chat_id = ?";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, chatId);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }





    public static String getUserName(long chatId) {
        String query = "SELECT first_name FROM userss WHERE chat_id = ?";
        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, chatId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) return rs.getString("first_name");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "Foydalanuvchi";
    }








}

