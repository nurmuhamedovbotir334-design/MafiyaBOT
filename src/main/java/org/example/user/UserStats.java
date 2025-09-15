package org.example.user;

import lombok.Data;

@Data
public class UserStats {
    private long chatId;
    private String firstName;
    private int dollar;
    private int diamond;
    private int shield;
    private boolean shieldActive;
    private int murderProtect;
    private boolean murderProtectActive;
    private int voteProtect;
    private boolean voteProtectActive;
    private int gun;
    private boolean gunActive;
    private int mask;
    private boolean maskActive;
    private int fakeDoc;
    private boolean fakeDocActive;
    private String role;
    private int win;
    private int playCount;
    private boolean is_Pro;
}
