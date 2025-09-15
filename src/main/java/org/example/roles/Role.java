package org.example.roles;

import lombok.Getter;

@Getter
public enum Role {
    CITIZEN("\uD83D\uDC68\uD83C\uDFFC Tinch aholi"),
    SHERIF("\uD83D\uDD75\uD83C\uDFFB\u200D♂ Komissar katani"),
    DOCTOR("\uD83D\uDC68\uD83C\uDFFB\u200D⚕ Doktor"),
    SLEEPWALKER("\uD83D\uDC83 Kezuvchi"),
    SERGEANT("\uD83D\uDC6E\uD83C\uDFFB\u200D♂ Serjant"),
    SUICIDE("\uD83E\uDD26\uD83C\uDFFC Suidsid"),
    KING("Podshoh"),
    GUARD("Qarovul"),
    LEADER("Lider"),
//    VAGRANT("Darbadar"),

    DON("\uD83E\uDD35\uD83C\uDFFB Don"),
//    LAWYER("\uD83D\uDC68\u200D\uD83D\uDCBC Advokat"),
    BIG_BRO("Aka"),
    LITTLE_BRO("Uka"),
    MAFIA("\uD83E\uDD35\uD83C\uDFFC Mafiya"),

    IMPOSTOR("Aferist"),
    KILLER("\uD83D\uDD2A Qotil"),
//    WIZARD("Sehrgar"),
    SORCERER("\uD83E\uDDDE\u200D♂\uFE0F Afsungar"),
    WOLF("\uD83D\uDC3A Bo‘ri"),
    FOX("Tulki"),
    ANGRY("G‘azabkor"),
    GENTLEMAN("Janob");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }
}
