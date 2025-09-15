package org.example.Group;

import org.example.dtabase.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.example.dtabase.Database.*;

public class PremiumGroupManager {

    // Guruhlarni DB dan yuklash
    public static List<PremiumGroup> loadGroups() {
        List<PremiumGroup> groups = new ArrayList<>();
        String query = "SELECT group_id, group_name, admin_id, expire_date, invite_link, amount FROM premium_groups";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PremiumGroup group = new PremiumGroup(
                        rs.getLong("group_id"),
                        rs.getString("group_name"),
                        rs.getLong("admin_id"),
                        rs.getDate("expire_date").toString(),
                        rs.getString("invite_link"),
                        rs.getInt("amount")
                );
                groups.add(group);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    // Guruhlarni DB ga saqlash (eski ma’lumotlarni tozalab, yangilarini yozadi)
    public static void saveGroups(List<PremiumGroup> groups) {
        String deleteQuery = "DELETE FROM premium_groups";
        String insertQuery = "INSERT INTO premium_groups (group_id, group_name, admin_id, expire_date, invite_link, amount) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             Statement deleteStmt = connection.createStatement()) {

            deleteStmt.executeUpdate(deleteQuery);

            try (PreparedStatement insertStmt = connection.prepareStatement(insertQuery)) {
                for (PremiumGroup group : groups) {
                    insertStmt.setLong(1, group.getGroupId());
                    insertStmt.setString(2, group.getGroupName());
                    insertStmt.setLong(3, group.getAdminId());
                    insertStmt.setDate(4, Date.valueOf(group.getExpireDate()));
                    insertStmt.setString(5, group.getInviteLink());
                    insertStmt.setInt(6, group.getAmount());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }

            System.out.println("✅ Premium guruhlar DB ga saqlandi!");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
