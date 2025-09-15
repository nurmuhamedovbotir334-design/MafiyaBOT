package org.example.Group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;


@NoArgsConstructor
@Data
@ToString
public class PremiumGroup {
    private long groupId;
    private String groupName;
    private long adminId;
    private String expireDate; // yyyy-MM-dd
    private String inviteLink; // yangi qo‘shilgan maydon
    private int amount;


    public PremiumGroup(Long groupId, String inviteLink, int amount, long l) {
        this.groupId = groupId;
        this.inviteLink = inviteLink;
        this.adminId = l;
        this.amount = amount;

    }

    public PremiumGroup(long groupId, String groupName, long adminId, String expireDate, String inviteLink, int amount) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.adminId = adminId;
        this.expireDate = expireDate;
        this.inviteLink = inviteLink;
        this.amount = amount;
    }


}
