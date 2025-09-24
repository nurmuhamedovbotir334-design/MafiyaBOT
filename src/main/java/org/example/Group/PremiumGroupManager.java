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

    public static void saveGroup(PremiumGroup group) {
        String insertQuery = "INSERT INTO premium_groups (group_id, group_name, admin_id, expire_date, invite_link, amount) " +
                "VALUES (?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (group_id) DO UPDATE SET " +
                "group_name = EXCLUDED.group_name, " +
                "admin_id = EXCLUDED.admin_id, " +
                "expire_date = EXCLUDED.expire_date, " +
                "invite_link = EXCLUDED.invite_link, " +
                "amount = premium_groups.amount + EXCLUDED.amount";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement(insertQuery)) {

            stmt.setLong(1, group.getGroupId());
            stmt.setString(2, group.getGroupName());
            stmt.setLong(3, group.getAdminId());
            stmt.setDate(4, Date.valueOf(group.getExpireDate()));
            stmt.setString(5, group.getInviteLink());
            stmt.setInt(6, group.getAmount());

            stmt.executeUpdate();

            System.out.println("✅ Premium guruh saqlandi yoki yangilandi!");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }



}
