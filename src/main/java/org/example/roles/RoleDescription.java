package org.example.roles;

import java.util.Map;

public class RoleDescription {
    public static final Map<Role, String> roleMessages = Map.ofEntries(
            Map.entry(Role.DON,
                    "Siz - *Don*! \n" +
                            "🔴 👑 Don – mafiya rahbari.\n" +
                            "🎯 Vazifangiz: har tun qaysi o‘yinchi uyg‘onmasligini hal qilish, ya’ni o‘ldirish.\n" +
                            "Sizning qaroringiz yakuniy hisoblanadi."),

            Map.entry(Role.CITIZEN,
                    "Siz - *Tinch aholi*! \n" +
                            "🔴 👤 Oddiy shaharlik.\n" +
                            "🎯 Vazifangiz: mafiyani topib, kunduzgi yig‘ilishda ularni osish."),

            Map.entry(Role.SHERIF,
                    "Siz - *Komissar*! \n" +
                            "🔴 🕵️‍♂️ Shahar himoyachisi.\n" +
                            "🎯 Vazifangiz: har tun bir kishini tekshirib, kim mafiyaligini aniqlash.\n" +
                            "⚠️ 1-tunda tekshirmasdan otish mumkin emas!"),

            Map.entry(Role.DOCTOR,
                    "Siz - *Doktor*! \n" +
                            "🔴 👨‍⚕️ Shahar shifokori.\n" +
                            "🎯 Vazifangiz: har tun bir kishini davolash.\n" +
                            "🩺 O‘zingizni faqat bir marta davolay olasiz."),

            Map.entry(Role.SLEEPWALKER,
                    "Siz - *Kezuvchi*! \n" +
                            "🔴 💃 Tun sayohatchisi.\n" +
                            "🎯 Vazifangiz: har tun kimningdir uyiga borib, uni chalg‘itish.\n" +
                            "⚠️ Komissarni uxlatib qo‘ymaslik muhim."),

            Map.entry(Role.SUICIDE,
                    "Siz - *Suidsid*! \n" +
                            "🎯 Vazifangiz: agar kunduzda osilsangiz, o‘yin sizning g‘alabangiz bilan tugaydi!"),

            Map.entry(Role.SERGEANT,
                    "Siz - *Serjant*! \n" +
                            "🔴 👮 Komissarning yordamchisi.\n" +
                            "🎯 Vazifangiz: Komissarga yordam berish, agar u o‘lsa – uning o‘rnini egallash."),

            Map.entry(Role.KING,
                    "Siz - *Podshoh*! \n" +
                            "👑 Shaharning ruhiy ustuni.\n" +
                            "🎯 Vazifangiz: tirik qolish. Agar siz osilsangiz va Qarovul sizni himoyalamasa – mafiyalar g‘alaba qiladi."),

            Map.entry(Role.GUARD,
                    "Siz - *Qarovul*! \n" +
                            "🛡️ Shaharning posboni.\n" +
                            "🎯 Vazifangiz: har tun bir kishini, ayniqsa Podshohni himoya qilish."),

            Map.entry(Role.LEADER,
                    "Siz - *Lider*! \n" +
                            "🌟 Yetakchi.\n" +
                            "🎯 Vazifangiz: agar kunduzda osilsangiz, sizga ovoz berganlar orasida nechta mafiyachi va nechta shaharlik borligini ochib berasiz."),

            Map.entry(Role.VAGRANT,
                    "Siz - *Darbadar*! \n" +
                            "👣 Tun kuzatuvchisi.\n" +
                            "🎯 Vazifangiz: jinoyatlarni yashirin kuzatish va shaharliklarga yordam berish."),

            Map.entry(Role.LAWYER,
                    "Siz - *Advokat*! \n" +
                            "👔 Mafiyaning himoyachisi.\n" +
                            "🎯 Vazifangiz: mafiyani himoya qilish. Agar siz ularni tanlasangiz, Komissar ularni oddiy aholi deb ko‘radi."),

            Map.entry(Role.BIG_BRO,
                    "Siz - *Aka*! \n" +
                            "🧔 Mafiyaning qarindoshi.\n" +
                            "🎯 Vazifangiz: Uka bilan birga qaror qabul qilish. Agar Don qaroriga zid bo‘lsa – sizning qaroringiz ustun bo‘ladi."),

            Map.entry(Role.LITTLE_BRO,
                    "Siz - *Uka*! \n" +
                            "👦 Mafiyaning qarindoshi.\n" +
                            "🎯 Vazifangiz: Akangiz bilan birga qaror qabul qilish."),

            Map.entry(Role.MAFIA,
                    "Siz - *Mafiya*! \n" +
                            "🔴 Oddiy mafiyachi.\n" +
                            "🎯 Vazifangiz: Don bilan birga shaharliklarni yo‘q qilish."),

            Map.entry(Role.IMPOSTOR,
                    "Siz - *Aferist*! \n" +
                            "🎭 Yolg‘onchi.\n" +
                            "🎯 Vazifangiz: o‘zingizni boshqa rolda ko‘rsatib, chalg‘itish."),

            Map.entry(Role.KILLER,
                    "Siz - *Qotil*! \n" +
                            "🔪 Yakkaxon qotil.\n" +
                            "🎯 Vazifangiz: har tun kimnidir o‘ldirish va yakka holda g‘alaba qozonish."),

            Map.entry(Role.WIZARD,
                    "Siz - *Sehrgar*! \n" +
                            "🧙 Sehr kuchiga ega.\n" +
                            "🎯 Vazifangiz: sehringiz bilan o‘yinga ta’sir qilish."),

            Map.entry(Role.SORCERER,
                    "Siz - *Afsungar*! \n" +
                            "🔮 Sehrli shaxs.\n" +
                            "🎯 Vazifangiz: mafiyaga yordam berish yoki neytral yo‘ldan o‘ynash."),

            Map.entry(Role.GENTLEMAN,
                    "Siz - *Janob*! \n" +
                            "🤵 Madaniyatli shaxs.\n" +
                            "🎯 Vazifangiz: o‘zingizni xotirjam tutib, tirik qolish."),

            Map.entry(Role.WOLF,
                    "Siz - *Bo‘ri*! \n" +
                            "🐺 Yovvoyi.\n" +
                            "🎯 Vazifangiz: yakka holda tirik qolish uchun hammani yo‘qotish."),

            Map.entry(Role.FOX,
                    "Siz - *Tulki*! \n" +
                            "🦊 Ayyor tulki.\n" +
                            "🎯 Vazifangiz: yashirinib, tirik qolishga harakat qilish."),

            Map.entry(Role.ANGRY,
                    "Siz - *G‘azabkor*! \n" +
                            "🔥 Portlovchi xarakter.\n" +
                            "🎯 Vazifangiz: agar sizni o‘ldirishsa – yoningizdagi odamni ham o‘zingiz bilan olib ketish.")

    );
}
