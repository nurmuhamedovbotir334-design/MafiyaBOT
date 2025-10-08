package org.example.bot;

import lombok.SneakyThrows;
import org.example.Group.PremiumGroup;
import org.example.Group.PremiumGroupManager;
import org.example.dtabase.Database;
import org.example.roles.Role;
import org.example.roles.RoleAssigner;
import org.example.roles.RoleDescription;
import org.example.room.GameLobby;
import org.example.room.LobbyManager;
import org.example.timer.TimerManager;
import org.example.user.UserStats;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.groupadministration.ExportChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.pinnedmessages.PinChatMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.example.dtabase.Database.*;

public class MafiaBot extends TelegramLongPollingBot {
    private final LobbyManager lobbyManager = new LobbyManager();
    private final Map<Long, Boolean> isWaitingForPaymentMap = new HashMap<>();
    private final Map<Long, Integer> paymentAmountMap = new HashMap<>();
    private final Map<Long, Integer> paymentMessageIdMap = new HashMap<>();
    private final TimerManager timerManager = new TimerManager();
    private final Map<Long, Integer> nightCountMap = new HashMap<>();
    private final Map<Long, Map<String, Integer>> voteCountMap = new HashMap<>();
    private final Map<Long, Boolean> voteClosed = new HashMap<>();
    private final Map<Long, Boolean> nightClosed = new HashMap<>();
    private final Map<Long, List<Map<String, String>>> callbackDataMap = new HashMap<>();
    private final Map<Long, Map<String, Set<Long>>> yesVotesMap = new HashMap<>();
    private final Map<Long, Map<String, Set<Long>>> noVotesMap = new HashMap<>();
    private final Map<Long, Map<Long, Long>> doctorLastTarget = new HashMap<>();
    private final Map<Long, Map<Long, Long>> kezLastTarget = new HashMap<>();
    private final Map<Long, Map<Long, Long>> qoravulLastTarget = new HashMap<>();
    private final Map<Long, Integer> activeGifts = new HashMap<>();
    private final Map<Long, Set<Long>> giftTakers = new HashMap<>();
    private final Map<Long, Set<Long>> doctorSelfUsed = new HashMap<>();
    Map<Long, Set<Long>> lastWordsWaiting = new HashMap<>();


    private final Map<Long, String> pendingPurchases = new HashMap<>();


    String admin_id = "8123768337";

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            Long chatId = update.getMessage().getChatId();

            if (pendingPurchases.containsKey(chatId)) {
                String diamondCount = pendingPurchases.get(chatId);
                String caption = update.getMessage().getCaption();
                ForwardMessage forward = new ForwardMessage();
                forward.setChatId(String.valueOf(admin_id));
                forward.setFromChatId(String.valueOf(chatId));
                forward.setMessageId(update.getMessage().getMessageId());
                execute(forward);

                InlineKeyboardButton okBtn = new InlineKeyboardButton("✅ Tasdiqlash");
                okBtn.setCallbackData("approve_" + chatId + "_" + diamondCount);

                InlineKeyboardButton cancelBtn = new InlineKeyboardButton("❌ Rad etish");
                cancelBtn.setCallbackData("reject_" + chatId);

                List<InlineKeyboardButton> row = Arrays.asList(okBtn, cancelBtn);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));

                SendMessage adminMsg = new SendMessage();
                adminMsg.setChatId(String.valueOf(admin_id));
                adminMsg.setText("🧾 Yangi chek!\n" +
                        "👤 User: " + chatId + "\n" +
                        "💎 Sotib olish: " + diamondCount + "\n" +
                        "📩 Caption: " + (caption != null ? caption : "yo‘q"));
                adminMsg.setReplyMarkup(markup);
                execute(adminMsg);
                SendMessage userMsg = new SendMessage();
                userMsg.setChatId(String.valueOf(chatId));
                userMsg.setText("✅ Chek qabul qilindi. Adminlar tez orada tekshiradi.");
                execute(userMsg);
                pendingPurchases.remove(chatId);
            } else {
                pendingPurchases.remove(chatId);
            }
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            String chatType = message.getChat().getType();
            if (chatType.equals("private")) {
                Long userId = message.getChatId();
                for (Map.Entry<Long, Set<Long>> entry : lastWordsWaiting.entrySet()) {
                    Long groupId = entry.getKey();
                    Set<Long> players = entry.getValue();

                    if (players.contains(userId) && message.hasText()) {
                        String text = message.getText();
                        GameLobby lobby = lobbyManager.getLobby(groupId);
                        String playerRole = lobby.getPlayerRole(userId);
                        sendMessage(groupId,
                                "Tunda " + playerRole + " - " + getUserMention(userId, groupId) + "ning  " + text + " degan ovozi eshitildi!");
                        players.remove(userId);
                        if (players.isEmpty()) {
                            lastWordsWaiting.remove(groupId);
                        }
                        return;
                    }
                }
                if (isWaitingForPaymentMap.getOrDefault(userId, false)) {
                    if (message.hasText() && message.getText().equalsIgnoreCase("/cancel")) {
                        isWaitingForPaymentMap.put(userId, false);
                        sendMessage(userId, "To'lov bekor qilindi.");
                    } else if (message.hasPhoto()) {
                        sendPhotoToAdminForApproval(message);
                        sendMessage(userId, "Pro sotib olish uchun so'rov yuborildi javobini kuting!");
                        isWaitingForPaymentMap.put(userId, false);
                    } else {
                        sendMessage(userId, "❗ Siz to'lov qilish holatidasiz chiqish uchun /cancel buyrug'ini yuboring.");
                        return;
                    }
                }

                if (message.hasText()) {
                    String text = message.getText();
                    String[] args = text.split(" ");
                    String userName = message.getFrom().getUserName();
                    String firstName = message.getFrom().getFirstName();
                    if (text.equals("/start")) {
                        if (isRegistered(userId)) {
                            if (isOwner(userId)) {
                                sendOwnerMenu(userId);
                            }
                            sendHelloMenu(userId, firstName);
                        } else {
                            sendAskSex(userId);
                        }
                    } else if (text.equals("/profile")) {
                        sendUserStatsWithButtons(userId, "");
                    } else if (args[0].equals("/start") && args.length > 1) {
                        if (isRegistered(userId)) {
                            String displayName = (userName != null) ? "@" + userName : firstName;
                            handleStartWithJoin(userId, new String[]{args[1]}, displayName);
                        } else {
                            sendAskSex(userId);
                        }
                    } else {
                        handleMafiaChat(userId, text);
                    }
                }
            } else if (chatType.equals("group") || chatType.equals("supergroup")) {
                Long groupId = message.getChatId();
                String text = message.getText() != null ? message.getText() : "";
                Long userId = message.getFrom().getId();

                GameLobby lobby = lobbyManager.getLobby(groupId);

                boolean isNight = !nightClosed.getOrDefault(groupId, true);

                if (lobby != null && lobby.isGameStarted()) {
                    boolean isPlayer = lobby.getPlayers().contains(userId);
                    boolean isBotCommand = text.startsWith("/almaz") || text.startsWith("/grsend") || text.contains("@CamelotMafiaBot");
                    boolean isAdminUser = isAdmin(groupId, userId);
                    boolean startsWithExclamation = text.startsWith("!");

                    if (isNight) {
                        // 🌙 Tun - hech kim yozolmaydi
                        try {
                            execute(DeleteMessage.builder()
                                    .chatId(groupId)
                                    .messageId(message.getMessageId())
                                    .build());
                            System.out.println("🌙 Tun: Xabar o‘chirildi (UserID=" + userId + ")");
                        } catch (Exception e) {
                            System.out.println("⚠️ Xabarni o‘chirib bo‘lmadi: " + e.getMessage());
                        }
                        return;
                    } else {
                        // 🌞 Kunduz - faqat o‘yinchilar yoki ! bilan yozgan adminlar yozishi mumkin
                        if (!(isPlayer || (isAdminUser && startsWithExclamation) || isBotCommand)) {
                            try {
                                execute(DeleteMessage.builder()
                                        .chatId(groupId)
                                        .messageId(message.getMessageId())
                                        .build());
                                System.out.println("❌ Kunduz: O‘yinchi emas, admin emas, xabar o‘chirildi: " + userId);
                            } catch (Exception e) {
                                System.out.println("⚠️ Xabarni o‘chirib bo‘lmadi: " + e.getMessage());
                            }
                            return;
                        }
                    }
                }


                if (message.isCommand()) {
                    if (!(text.startsWith("/almaz") || text.startsWith("/grsend") || text.equals("/leave")|| text.equals("/setting") || text.contains("@CamelotMafiaBot"))) {
                        return;
                    }

                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(groupId);

                    DeleteMessage deleteCommand = new DeleteMessage();
                    deleteCommand.setChatId(groupId);
                    deleteCommand.setMessageId(message.getMessageId());
                    if (!(text.startsWith("/almaz") || text.startsWith("/grsend"))) {
                        execute(deleteCommand);
                    }

                    GetChatMember getChatMember = new GetChatMember();
                    getChatMember.setChatId(groupId.toString());
                    getChatMember.setUserId(getMe().getId());
                    ChatMember chatMember = execute(getChatMember);
                    String status = chatMember.getStatus();

                    if (message.getNewChatMembers() != null && !message.getNewChatMembers().isEmpty()) {
                        for (User newUser : message.getNewChatMembers()) {
                            if (newUser.getId().equals(getMe().getId())) {
                                SendMessage welcomeMsg = new SendMessage();
                                welcomeMsg.setChatId(message.getChatId());
                                welcomeMsg.setText("\uD83D\uDC4B Salom!:\n" +
                                        "/game - ro'yxatni ochish\n" +
                                        "/start - o'yinni boshlash\n" +
                                        "/extend - vaqtni uzaytirish\n" +
                                        "/stop - o'yinni to'xtatish\n" +
                                        "/almaz - (give qoidasi /almaz 10 )\n" +
                                        "/grsend - guruhni premium qilish.");
                                execute(welcomeMsg);
                            }
                        }
                    }
                    else if (text.equals("/leave")) {
                        if (lobby == null) {
//                            sendMessage.setText("❌ Siz hozirda hech qanday o'yinda emassiz.");
//                            execute(sendMessage);
                            return;
                        }

                        Long playerId = message.getFrom().getId();
                        String playerName = message.getFrom().getFirstName();
                        String mention = "<a href=\"tg://user?id=" + playerId + "\">" + playerName + "</a>";

                        String role = lobby.getPlayerRole(playerId);

                        String messageText;
                        if (!lobby.isGameStarted()) {
                            messageText = "O'yinchi " + mention + " ro'yxatdan chiqdi.";
                            lobby.leavePlayer(playerId);
                        } else {
                            messageText = mention + " Bu shaharning yovuzliklariga chiday olmadi va o‘zini osib qo‘ydi.\nU " + role + " edi";
                            lobby.killPlayer(playerId);
                            checkGameOver(groupId);
                        }

                        sendMessage.setText(messageText);
                        sendMessage.setParseMode("HTML");
                        execute(sendMessage);
                        return;
                    }

                    if (text.startsWith("/almaz")) {
                        String[] parts = text.split(" ");
                        int amount;

                        try {
                            amount = Integer.parseInt(parts[1]);
                        } catch (Exception e) {
                            return;
                        }

                        if (amount <= 0) {   // faqat musbat son bo‘lsa davom etadi
                            return;
                        }

                        Long senderId = message.getFrom().getId();
                        String senderName = message.getFrom().getFirstName();
                        ensureUserExists(senderId, senderName);

                        double senderBalance = getBalance(senderId);
                        if (senderBalance < amount) {
                            return;
                        }

                        String senderLink = "<a href=\"tg://user?id=" + senderId + "\">" + senderName + "</a>";

                        if (message.getReplyToMessage() != null) {
                            Long targetId = message.getReplyToMessage().getFrom().getId();
                            String targetName = message.getReplyToMessage().getFrom().getFirstName();

                            updateBalanceALMZ(senderId, senderBalance - amount);
                            addBalanceALMZ(targetId, 0, amount);
                            ensureUserExists(targetId, targetName);

                            boolean isPro = isUserPro(senderId);
                            String targetLink = "<a href=\"tg://user?id=" + targetId + "\">" + targetName + "</a>";

                            SendMessage replyMsg = new SendMessage();
                            replyMsg.setChatId(groupId);
                            if (isPro) {
                                replyMsg.setText("⭐️ PRO a’zo " + senderLink + " " + amount +
                                        " ta almazni 💎 " + targetLink + " ga topshirdi!");
                            } else {
                                replyMsg.setText(senderLink + " " + amount +
                                        " ta almazni 💎 " + targetLink + " ga uzatdi!");
                            }
                            replyMsg.setParseMode("HTML");
                            execute(replyMsg);
                        } else {
                            updateBalanceALMZ(senderId, senderBalance - amount);

                            boolean isPro = isUserPro(senderId);
                            String textMsg;
                            if (isPro) {
                                textMsg = "⭐️ PRO a’zo " + senderLink + " " + amount +
                                        " dona 💎 ulashmoqda!\n💎 olish uchun pastdagi tugmani bosing!";
                            } else {
                                textMsg = senderLink + " saxovat bilan " + amount +
                                        " dona 💎 ulashmoqda!\n💎 olish uchun pastdagi tugmani bosing!";
                            }

                            activeGifts.put(senderId, amount);
                            giftTakers.put(senderId, new HashSet<>());

                            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                                    .text("almaz 💎")
                                    .callbackData("take_diamond:" + senderId)
                                    .build();

                            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                                    .keyboardRow(List.of(btn))
                                    .build();

                            SendMessage groupMsg = SendMessage.builder()
                                    .chatId(groupId)
                                    .text(textMsg)
                                    .replyMarkup(markup)
                                    .parseMode("HTML")
                                    .build();

                            execute(groupMsg);
                        }
                    } else if (text.startsWith("/grsend")) {
                        String[] parts = text.split(" ");
                        if (parts.length < 2) {
                            execute(sendMessage);
                            return;
                        }
                        int amount;

                        try {
                            amount = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            execute(sendMessage);
                            return;
                        }


                        if (amount <= 0) {   // faqat musbat son bo‘lsa davom etadi
                            return;
                        }

                        Long adminId = message.getFrom().getId();
                        double currentBalance = getBalance(adminId);

                        if (currentBalance < amount) {
                            execute(sendMessage);
                            return;
                        }

                        updateBalanceALMZ(adminId, currentBalance - amount);

                        Chat chat = message.getChat();
                        String groupName = chat.getTitle();

                        String inviteLink;
                        if (chat.getUserName() != null) {
                            inviteLink = "https://t.me/" + chat.getUserName();
                        } else {
                            ExportChatInviteLink export = new ExportChatInviteLink(String.valueOf(groupId));
                            inviteLink = execute(export);
                        }

                        String expireDate = LocalDate.now().plusDays(30).toString();

                        String senderName = message.getFrom().getFirstName();
                        String senderLink = "<a href=\"tg://user?id=" + adminId + "\">" + senderName + "</a>";

                        String notifyText = "💎 " + senderLink + " --- " + groupName + " guruhi uchun "
                                + amount + " ta almazni hadya qildi! 🎉";

                        SendMessage notifyMsg = SendMessage.builder()
                                .chatId(groupId)
                                .text(notifyText)
                                .parseMode("HTML")
                                .build();
                        execute(notifyMsg);

                        PremiumGroup newGroup = new PremiumGroup(
                                groupId,
                                groupName,
                                adminId,
                                expireDate,
                                inviteLink,
                                amount
                        );

                        PremiumGroupManager.saveGroup(newGroup);
                    } else if (text.equals("/extend@CamelotMafiaBot")) {
                        if (lobby == null) {
                            sendMessage.setText("❌ Hozircha o‘yin yoki ro‘yxatdan o‘tish jarayoni yo‘q!");
                            execute(sendMessage);
                            return;
                        }
                        String currentStage = lobby.getCurrentStage();
                        if (!"Ro'yxatdan o'tish".equals(currentStage)) {
                            return;
                        }

                        if (lobby.isExtended()) {
                            TimerManager.cancelTimerForGroup(groupId);
                            lobby.setForceStart(true);
                        } else {
                            timerManager.extendTimer(groupId, 2);
                            lobby.setExtended(true);
                            sendMessage.setText("⏳ Registratsiya vaqti uzaytirildi!");
                            execute(sendMessage);
                            return;
                        }
                    }

                    if (!isAdmin(groupId, userId)) {
                        return;
                    }

                    if (text.equals("/game@CamelotMafiaBot")) {
                        if (!status.equals("administrator") && !status.equals("creator")) {
                            sendMessage.setText("❌ Iltimos, botni guruhga admin qilib qo‘ying.");
                            execute(sendMessage);
                            return;
                        }
                        if (lobbyManager.getLobby(groupId) == null) {
                            handleGameCommand(groupId);
                        } else {
                            execute(DeleteMessage.builder()
                                    .chatId(groupId)
                                    .messageId(lobby.getMessageId())
                                    .build());
                            sendCurrentLobbyStatus(groupId);
                        }
                    } else if (text.equals("/stop@CamelotMafiaBot")) {
                        if (lobby == null) {
                            sendMessage.setText("❌ Hozircha o‘yin yoki ro‘yxatdan o‘tish jarayoni yo‘q!");
                            execute(sendMessage);
                            return;
                        }
                        try {
                            if (lobbyManager.getLobby(groupId) != null) {
                                execute(DeleteMessage.builder()
                                        .chatId(groupId)
                                        .messageId(lobby.getMessageId())
                                        .build());
                            }
                        } catch (Exception e) {
                            System.out.println("⚠️ Registr xabarini o‘chirib bo‘lmadi: " + e.getMessage());
                        }

                        TimerManager.cancelTimerForGroup(groupId);
                        lobbyManager.removeLobby(groupId);
                        TimerManager.cancelTimerForGroup(groupId);
                        voteCountMap.remove(groupId);
                        yesVotesMap.remove(groupId);
                        noVotesMap.remove(groupId);
                        doctorLastTarget.remove(groupId);
                        qoravulLastTarget.remove(groupId);
                        doctorSelfUsed.remove(groupId);
                        pendingPurchases.remove(groupId);
                        doctorSelfUsed.remove(groupId);
                        nightCountMap.remove(groupId);
                        lastWordsWaiting.remove(groupId);
                        sendMessage.setText("O‘yin to‘xtatildi!");
                    } else if (text.equals("/start@CamelotMafiaBot")) {
                        if (lobby == null) {
                            handleGameCommand(groupId);
                            return;
                        }

                        String currentStage = lobby.getCurrentStage();
                        if (!"Ro'yxatdan o'tish".equals(currentStage)) {
                            return;
                        }
                        try {
                            if (lobby != null) {
                                execute(DeleteMessage.builder()
                                        .chatId(groupId)
                                        .messageId(lobby.getMessageId())
                                        .build());
                            }
                        } catch (Exception e) {
                            System.out.println("Xatolik message o'chirishda: " + e.getMessage());
                        }

                        int count = lobby.getPlayers().size();
                        if (count < 3) {
                            sendMessage(groupId, "❌ O‘yinni boshlash uchun kamida 4 ta ishtirokchi kerak.");
                            lobbyManager.removeLobby(groupId);
                            TimerManager.cancelTimerForGroup(groupId);
                            return;
                        }
                        startGame(lobby, groupId);
                        lobby.startGame();
                    }else if (text.equals("/setting")) {
                        // faqat adminlar uchun
                        String groupName = message.getChat().getTitle();

                        // adminni lichkasiga yozamiz
                        int currentTime = Database.getGroupRegistrationTime(groupId);
                        SendMessage privateMsg = new SendMessage();
                        privateMsg.setChatId(String.valueOf(message.getFrom().getId()));
                        privateMsg.setText("⚙️ Guruh: " + groupName + "\n" +
                                "Hozirgi ro‘yxatdan o‘tish vaqti: " + currentTime + " sekkund\n\n" +
                                "⬇️ Quyidan yangi vaqtni tanlang:");

                        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                        int[] times = {30, 45, 60, 90, 120, 240};
                        for (int i = 0; i < times.length; i += 3) {
                            List<InlineKeyboardButton> row = new ArrayList<>();
                            for (int j = i; j < i + 3 && j < times.length; j++) {
                                InlineKeyboardButton btn = new InlineKeyboardButton();
                                btn.setText(times[j] + " sec");
                                btn.setCallbackData("settime:" + groupId + ":" + times[j]);
                                row.add(btn);
                            }
                            rows.add(row);
                        }
                        markup.setKeyboard(rows);
                        privateMsg.setReplyMarkup(markup);
                        execute(privateMsg);

                        sendMessage(groupId, "⚙️ Admin sozlamalarni o‘zgartirmoqda...");
                    }
                    execute(sendMessage);
                }
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            Long userId = callbackQuery.getFrom().getId();

            if (!isWaitingForPaymentMap.getOrDefault(userId, false)) {
                handleCallback(callbackQuery);
            }
        }
    }

    private void ensureUserExists(Long userId, String firstName) {
        if (!isRegistered(userId)) {
            saveUser(userId, firstName, null);
        }
    }

    private boolean isAdmin(Long groupId, Long userId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(groupId.toString());
            getChatMember.setUserId(userId);

            ChatMember chatMember = execute(getChatMember);
            String status = chatMember.getStatus();

            return status.equals("administrator") || status.equals("creator");
        } catch (Exception e) {
            return false;
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) throws TelegramApiException, SQLException {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId(); // Bu groupId bo'lishi mumkin
        int messageId = callbackQuery.getMessage().getMessageId();
        String firstName = callbackQuery.getFrom().getFirstName();
        Long playerId = callbackQuery.getFrom().getId(); // Harakat qilgan user ID

        if (data.startsWith("settime:")) {
            String[] parts = data.split(":");
            if (parts.length < 3) return;
            long groupId = Long.parseLong(parts[1]);
            int newTime = Integer.parseInt(parts[2]);

            Database.setGroupRegistrationTime(groupId, newTime);

            // Adminni xabardor qilamiz
            sendMessage(chatId, "✅ Ro‘yxatdan o‘tish vaqti " + newTime + " daqiqa qilib o‘zgartirildi.");

            // Guruhga ham xabar beramiz
            return;
        }


        String[] partsgroupId = data.split("_");
        if (data.startsWith("w_")) {
            long groupId1 = Long.parseLong(partsgroupId[3]);
            GameLobby lobby = lobbyManager.getLobby(groupId1);

            if (lobby != null) {
                Map<Long, Integer> inactivityCount = lobby.getInactivityCount();
                if (inactivityCount == null) {
                    inactivityCount = new ConcurrentHashMap<>();
                    lobby.setInactivityCount(inactivityCount);
                }

                // ✅ Harakat qilgan userni 0 qilamiz
                inactivityCount.put(playerId, 0);
                System.out.println("✅ Player " + playerId + " action qildi, hisob 0 qilindi 1 1.");
            }
            if (data.startsWith("skip_vote_")) {
                String[] parts = data.split("_");
                long targetPlayerId = Long.parseLong(parts[2]);
                long groupId = Long.parseLong(parts[3]);
                String userMention = "<a href=\"tg://user?id=" + targetPlayerId + "\">" + getCleanFirstName(targetPlayerId, groupId) + "</a>";
                String message = userMention + " ovoz bermaslikka qaror qildi!";

                try {
                    execute(SendMessage.builder()
                            .chatId(groupId)
                            .text(message)
                            .parseMode("HTML")
                            .build());
                    execute(DeleteMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .messageId(callbackQuery.getMessage().getMessageId())
                            .build());
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (data.equals("VIEW_ROLE")) {
                Long userId = callbackQuery.getFrom().getId();
                if (lobby != null) {
                    Role role = lobby.getPlayerRoles().get(userId);
                    if (role != null) {
                        showModal(callbackQuery, "🕵️ Sizning rolingiz: " + role.getDisplayName());
                    } else {
                        showModal(callbackQuery, "❌ Siz ushbu o‘yinda ishtirokchi emassiz.");
                    }
                } else {
                    showModal(callbackQuery, "❌ O‘yin hali boshlanmagan yoki topilmadi.");
                }
            }
//            if (data.startsWith("skip_action:")) {
//                String[] parts = data.split(":");
//                if (parts.length < 4) return;
//
//                String roleName = parts[1];
//                long groupId = Long.parseLong(parts[2]);
//                long targetPlayerId = Long.parseLong(parts[3]);
//
//                Role role = Role.valueOf(roleName);
//                String groupMessage = role.getDisplayName() + " hech kimni tanlamaslikka qaror qildi..";
//
//                try {
//                    // ✅ Guruhga xabar
//                    execute(SendMessage.builder()
//                            .chatId(groupId)
//                            .text(groupMessage)
//                            .build());
//
//                    // ✅ O‘sha userga shaxsiy xabar
//                    execute(SendMessage.builder()
//                            .chatId(targetPlayerId)
//                            .text("❗ Siz bu tun harakatni o‘tkazib yubordingiz. (Inaktivlik hisoblanmaydi ✅)")
//                            .build());
//
//                    // Inline tugmani o‘chirib tashlash
//                    execute(DeleteMessage.builder()
//                            .chatId(String.valueOf(targetPlayerId))
//                            .messageId(callbackQuery.getMessage().getMessageId())
//                            .build());
//
//                    // ✅ Inaktivlik hisobini NOLLAB qo‘yamiz
//                    if (lobby != null) {
//                        Map<Long, Integer> inactivityCount = lobby.getInactivityCount();
//                        if (inactivityCount == null) {
//                            inactivityCount = new ConcurrentHashMap<>();
//                            lobby.setInactivityCount(inactivityCount);
//                        }
//
//                        // O‘tkazib yuborsa – +1 qo‘shilmasin, balki 0 bo‘lsin
//                        inactivityCount.put(targetPlayerId, 0);
//                    }
//
//                } catch (TelegramApiException e) {
//                    e.printStackTrace();
//                }
//                return;
//            }

        }

        if (data.startsWith("PAY_CONFIRM_")) {
            Long userChatId = Long.parseLong(data.split("_")[2]);
            int days = paymentAmountMap.getOrDefault(userChatId, 7); // Callbackdan kelgan days ni olish
            updateProStatus(userChatId, days);
            sendMessage(userChatId, "🎉 To'lov tasdiqlandi! Sizda endi " + days + " kunlik Pro statusi faol!");
            sendMessage(Long.valueOf(admin_id), "✅ " + userChatId + " uchun to'lov tasdiqlandi (" + days + " kun).");
            return;
        } else if (data.startsWith("PAY_REJECT_")) {
            Long userChatId = Long.parseLong(data.split("_")[2]);
            isWaitingForPaymentMap.remove(userChatId);
            paymentAmountMap.remove(userChatId);
            paymentMessageIdMap.remove(userChatId);
            sendMessage(userChatId, "❌ To'lov rad etildi. Iltimos, qayta urinib ko'ring!");
            sendMessage(Long.valueOf(admin_id), "❌ " + userChatId + " uchun to'lov rad etildi.");
            return;
        }

        if (data.equals("tariff_30_45")) {
            Long userChatId = callbackQuery.getMessage().getChatId(); // yoki boshqa joydan ol
            int cost = 45;   // 45 olmos
            int days = 30;   // 30 kun

            try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD)) {
                // Foydalanuvchini olmosini tekshirish
                String checkSql = "SELECT olmos FROM userss WHERE chat_id = ?";
                PreparedStatement checkStmt = connection.prepareStatement(checkSql);
                checkStmt.setLong(1, userChatId);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    int currentOlmos = rs.getInt("olmos");

                    if (currentOlmos >= cost) {
                        // Olmosni kamaytirish
                        String updateOlmosSql = "UPDATE userss SET olmos = olmos - ? WHERE chat_id = ?";
                        PreparedStatement updateOlmosStmt = connection.prepareStatement(updateOlmosSql);
                        updateOlmosStmt.setInt(1, cost);
                        updateOlmosStmt.setLong(2, userChatId);
                        updateOlmosStmt.executeUpdate();

                        // Pro statusni yangilash (30 kun qo‘shish)
                        updateProStatus(userChatId, days);

                        sendMessage(userChatId, "🎉 Tabriklaymiz! Siz " + days + " kunlik Pro tarifini oldingiz. (" + cost + " olmos hisobingizdan ayirildi)");
                    } else {
                        sendMessage(userChatId, "❌ 💎 Hisobingizda olmos yetarli emas! (" + cost + " olmos kerak)");
                    }
                } else {
                    sendMessage(userChatId, "⚠️ Siz avval ro‘yxatdan o‘tmagansiz!");
                }

            } catch (SQLException e) {
                e.printStackTrace();
                sendMessage(userChatId, "⚠️ Xatolik yuz berdi. Qayta urinib ko‘ring!");
            }
            return;
        }

        if (data.startsWith("approve_")) {
            String[] parts = data.split("_"); // approve_userId_diamondCount
            Long userId = Long.parseLong(parts[1]);
            String diamondCount = parts[2];

            // ✅ Balansni yangilash
            updateBalance(userId, Double.parseDouble(diamondCount));

            // Userga xabar
            SendMessage userMsg = new SendMessage();
            userMsg.setChatId(String.valueOf(userId));
            userMsg.setText("🎉 To‘lov tasdiqlandi! Sizga " + diamondCount + " ta olmos qo‘shildi.\n/profile ni bosib tekshirib koʻring");
            execute(userMsg);

            // Adminni xabardor qilish
            sendMessage(callbackQuery.getMessage().getChatId(), "✅ Tasdiqlandi!");

            // 🔥 Tugmalarni olib tashlash
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(String.valueOf(callbackQuery.getMessage().getChatId()));
            editMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
            editMarkup.setReplyMarkup(null); // tugmalarni olib tashlaymiz
            execute(editMarkup);

            pendingPurchases.remove(userId);
            return;

        } else if (data.startsWith("reject_")) {
            Long userId = Long.parseLong(data.split("_")[1]);

            // Userga xabar
            SendMessage userMsg = new SendMessage();
            userMsg.setChatId(String.valueOf(userId));
            userMsg.setText("❌ To‘lov rad etildi. Iltimos, qayta urinib ko‘ring.\n/profile ni bosib tekshirib koʻring");
            execute(userMsg);

            // Adminni xabardor qilish
            sendMessage(callbackQuery.getMessage().getChatId(), "❌ Rad etildi!");

            // 🔥 Tugmalarni olib tashlash
            EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
            editMarkup.setChatId(String.valueOf(callbackQuery.getMessage().getChatId()));
            editMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
            editMarkup.setReplyMarkup(null); // tugmalarni olib tashlaymiz
            execute(editMarkup);

            pendingPurchases.remove(userId);
            return;
        }


        if (data.startsWith("buyDM_")) {
            String diamondCount = data.substring(6);

            pendingPurchases.put(chatId, diamondCount);

            SendMessage payMsg = new SendMessage();
            payMsg.setChatId(String.valueOf(chatId));
            payMsg.setParseMode("HTML");
            payMsg.setText(
                    "💎 <b>Siz " + diamondCount + " ta olmos sotib olishni tanladingiz.</b>\n\n" +
                            "💳 <b>To‘lovni quyidagi karta raqamiga yuboring:</b>\n" +
                            "<b>9860 3501 4855 1277</b>\n" +
                            "👤 <b>Normurodov Mehrojbek</b>\n\n" +
                            "📸 <b>Chekni shu yerga tashlang.</b>\n\n" +
                            "⚠️ <b>Eslatma: Chek yuborganingizdan so‘ng, adminlar uni tekshiradi (1 minut – 1 soat ichida).</b>"
            );

            execute(payMsg);
            return;
        }


        if (data.startsWith("w_") && !isCurrentGameCallback(data)) {
            showModal(callbackQuery, "⛔ Bu tugma avvalgi o‘yinga tegishli!");
            return;
        }

        switch (data) {
            case "main_menu":
                sendHelloMenu(chatId, firstName);
                break;
            case "gender_male":
            case "gender_female":
                saveUser(chatId, firstName, data.equals("gender_male") ? "male" : "female");
                sendHelloMenu(chatId, firstName);
                deleteMessage(chatId, messageId);
                break;
            case "open_shop":
                sendShopMenu(chatId);
                deleteMessage(chatId, messageId);
                break;
            case "buy_dlr":
                sendPurchaseOptions(chatId);
                deleteMessage(chatId, messageId);
                break;
            case "buy_dmnd":
                sendBuyOptionsWithPrices(chatId);
                deleteMessage(chatId, messageId);
                break;
            case "back_main_menu":
                sendUserStatsWithButtons(chatId, "");
                deleteMessage(chatId, messageId);
                break;
            case "premium_groups": {
                showPremiumGroups(chatId, 0, callbackQuery.getMessage().getMessageId()); // 0-sahifadan boshlaymiz
                break;
            }
            default:
                if (data.startsWith("by_")) {
                    String[] parts = data.split("_");
                    String name = parts[1];
                    int price = Integer.parseInt(parts[2]);
                    String type = "💵".equals(parts[3]) ? "dollar" : "💎".equals(parts[3]) ? "diamond" : parts[3];
                    System.out.println(name);
                    System.out.println(price);
                    System.out.println(type);
                    switch (name) {
                        case "reset":
                            boolean isDiamond = type.equalsIgnoreCase("diamond");
                            int balance = isDiamond ? getDiamondCount(chatId) : getDollarByChatId(chatId);
                            if (balance >= price) {
                                if (isDiamond) {
                                    decreaseDiamond(chatId, price);
                                } else {
                                    decreaseDollar(chatId, price);
                                }
                                resetGameStats(chatId);
                                showModal(callbackQuery, "🔄Statistika muvaffaqiyatli yangilandi!");
                            } else {
                                showModal(callbackQuery, "Hisobingizda " + (isDiamond ? "Olmos" : "Pul") + " yetarli emas!");
                            }
                            break;
                        case "shield":
                            handleBuyItem(callbackQuery, chatId, price, "shield", type, "🛡 Himoya muvofaqiyatli sotib olindi!");
                            break;
                        case "killershield":
                            handleBuyItem(callbackQuery, chatId, price, "killershield", type, "🛑 Qotildan himoya muvofiyaqiyatli sotib olindi!");
                            break;
                        case "voteshield":
                            handleBuyItem(callbackQuery, chatId, price, "voteshield", type, "⚖️ Ovoz berishni himoya qilish muvofiyaqiyatli sotib olindi!");
                            break;
                        case "gun":
                            handleBuyItem(callbackQuery, chatId, price, "gun", type, "🔫 Miltiq muvofiyaqiyatli sotib olindi!");
                            break;
                        case "mask":
                            handleBuyItem(callbackQuery, chatId, price, "mask", type, "🎭 Maska muvofaqiyatli sotib olindi!");
                            break;
                        case "fakedoc":
                            handleBuyItem(callbackQuery, chatId, price, "fakedoc", type, "📁Hujjat muvofiyaqiyatli sotib olindi!");
                            break;
                    }
                } else if (data.startsWith("toggle_")) {
                    String type = data.split("_")[1];
                    toggleItemActiveStatus(chatId, type);
                    sendUserStatsWithButtons(chatId, "");
                    deleteMessage(chatId, messageId);
                } else if (data.startsWith("buy_dollar_")) {
                    String[] parts = data.split("_");
                    String dollarStr = parts[2];
                    int diamondStr = Integer.parseInt(parts[3]);
                    int diamond = getDiamondCount(chatId);
                    if (diamond >= diamondStr) {
                        int dollarAmount = Integer.parseInt(dollarStr);
                        convertDiamondToDollar(chatId, dollarAmount, diamondStr);
                        System.out.println(diamond);
                        showModal(callbackQuery, dollarStr + " 💸 muvofiyaqiyatli sotib olindi!");
                    } else {
                        showModal(callbackQuery, "Hisobingizda olmos yetarli emas!");
                    }
                } else if (data.equals("buy_pro")) {
                    if (!isUserPro(chatId)) {
                        deleteMessage(chatId, messageId);
                        sendProOptions(chatId);
                    } else {
                        showModal(callbackQuery, "Sizda Pro versiya davri hali tugamagan!");
                    }
                } else if (data.equals("pro_real")) {
                    deleteMessage(chatId, messageId);
                    sendTariffOptionsReal(chatId);
                } else if (data.equals("pro_almaz")) {
                    deleteMessage(chatId, messageId);
                    sendTariffOptions(chatId);
                } else if (data.equals("back_to_pursache")) {
                    deleteMessage(chatId, messageId);
                    sendProOptions(chatId);
                } else if (data.startsWith("TARIFF_")) {
                    deleteMessage(chatId, messageId);
                    String[] parts = data.split("_");
                    int day = Integer.parseInt(parts[1]);
                    int price = Integer.parseInt(parts[2]);
                    String currency = parts[3];
                    sendPaymentInstruction(chatId, price, day, currency);
                } else if (data.startsWith("w_vote_")) {
                    voteMessage(callbackQuery);
                } else if (data.startsWith("w_kom_")) {
                    String[] parts = data.split("_");
                    if (!nightClosed.get(Long.parseLong(parts[3]))) {
                        KomissarAction(callbackQuery);
                    }
                } else if (data.startsWith("w_kam_") || data.startsWith("w_don_") || data.startsWith("w_doc_")
                        || data.startsWith("w_mafia_") || data.startsWith("w_kez_")
                        || data.startsWith("w_qotil_") || data.startsWith("w_qor_")
                        || data.startsWith("w_BIGBRO_") || data.startsWith("w_LITTLEBRO_")) {

                    String[] parts = data.split("_");

                    Long groupId = Long.parseLong(parts[3]);
                    if (!nightClosed.get(groupId)) {
                        // 👇 to‘g‘ri parametrlarda yuboramiz
                        String prefix = parts[1];         // don, mafia, doc...
                        String action = parts[2];         // kill, heal...
                        String otherPlayerId = parts[5];  // target
                        Long playerId1 = Long.parseLong(parts[6]); // kim bosdi

                        manageActions(groupId, action, prefix, otherPlayerId, playerId1);
                        handleAction(callbackQuery);
                    }
                } else if (data.startsWith("vote_yes_") || data.startsWith("vote_no_")) {
                    String[] parts = data.split("_");
                    String voteType = parts[1];
                    String targetName = parts[2];
                    updateVoteButtons(callbackQuery, voteType, targetName);
                } else if (data.startsWith("premium_prev_")) {
                    int page = Integer.parseInt(data.replace("premium_prev_", ""));
                    showPremiumGroups(chatId, page, callbackQuery.getMessage().getMessageId());

                } else if (data.startsWith("premium_next_")) {
                    int page = Integer.parseInt(data.replace("premium_next_", ""));
                    showPremiumGroups(chatId, page, callbackQuery.getMessage().getMessageId());

                } else if (data.startsWith("take_diamond:")) {
                    Long giverId = Long.parseLong(data.split(":")[1]);
                    Long takerId = callbackQuery.getFrom().getId();
                    String takerName = callbackQuery.getFrom().getFirstName();

                    ensureUserExists(takerId, takerName);

                    synchronized (activeGifts) { // 🔒 Race condition oldini olish
                        int left = activeGifts.getOrDefault(giverId, 0);

                        if (left <= 0) {
                            answerCallbackQuery(callbackQuery.getId(), "❌ Sovg‘a tugadi!");
                            return;
                        }

                        // ❌ Agar allaqachon olgan bo‘lsa
                        if (giftTakers.getOrDefault(giverId, new HashSet<>()).contains(takerId)) {
                            answerCallbackQuery(callbackQuery.getId(), "❌ Siz allaqachon oldingiz!");
                            return;
                        }

                        // ✅ Balansni yangilaymiz
                        addBalanceALMZ(takerId, 0, 1);

                        // 📝 Ro‘yxatga qo‘shamiz
                        giftTakers.computeIfAbsent(giverId, k -> new HashSet<>()).add(takerId);
                        left -= 1;
                        activeGifts.put(giverId, left);

                        // 🔢 Ro‘yxat tuzamiz
                        StringBuilder sb = new StringBuilder();

                        // Donator (havola bilan)
                        sb.append("💎 <a href=\"tg://user?id=")
                                .append(giverId).append("\">")
                                .append(getUserName(giverId))
                                .append("</a> almaz ulashmoqda!\n\n");

                        // Olganlar
                        sb.append("🏆 G‘oliblar:\n");
                        int i = 1;
                        for (Long id : giftTakers.get(giverId)) {
                            sb.append(i++).append(") <a href=\"tg://user?id=")
                                    .append(id).append("\">")
                                    .append(getUserName(id))
                                    .append("</a> - 1 💎\n");
                        }

                        // 🔄 Eski xabarni yangilaymiz
                        EditMessageText.EditMessageTextBuilder editBuilder = EditMessageText.builder()
                                .chatId(callbackQuery.getMessage().getChatId().toString())
                                .messageId(callbackQuery.getMessage().getMessageId())
                                .text(sb.toString())
                                .parseMode("HTML"); // 👈 linklar ishlashi uchun

                        if (left > 0) {
                            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                                    .text("almaz 💎 (qoldi: " + left + ")")
                                    .callbackData("take_diamond:" + giverId)
                                    .build();

                            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                                    .keyboardRow(List.of(btn))
                                    .build();

                            editBuilder.replyMarkup(markup);
                        }

                        try {
                            execute(editBuilder.build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        answerCallbackQuery(callbackQuery.getId(), "✅ Siz 1 dona 💎 oldingiz!");
                        if (left <= 0) {
                            activeGifts.remove(giverId);
                            giftTakers.remove(giverId);
                        }
                    }
                } else {
                    showModal(callbackQuery, "Bu funksiya hali ishga tushmadi!");
                }
                break;
        }
    }

    private void answerCallbackQuery(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQueryId);
        answer.setText(text);
        answer.setShowAlert(true);
        try {
            execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleMafiaChat(Long senderId, String text) {
        GameLobby lobby = lobbyManager.getLobbyByPlayer(senderId);
        if (lobby == null) {
            sendSafe(senderId, "❌ Siz hozirda hech qanday o'yinda emassiz.");
            return;
        }

        if (!lobby.isInGame(senderId) || !lobby.isAlive(senderId)) {
            sendSafe(senderId, "❌ Siz o‘yinda faol emassiz.");
            return;
        }

        String playerRole = lobby.getPlayerRole(senderId);
        if (playerRole == null) {
            sendSafe(senderId, "❌ Sizning rolingiz aniqlanmadi.");
            return;
        }

        String cleanedRole = playerRole
                .replaceAll("[^a-zA-Zа-яА-Я0-9_ ]", "") // faqat harf, raqam, probel qoldiradi
                .trim()
                .toLowerCase();

        Role role;
        switch (cleanedRole) {
            case "mafiya", "mafia" -> role = Role.MAFIA;
            case "don" -> role = Role.DON;
            case "big_bro", "katta aka" -> role = Role.BIG_BRO;
            case "little_bro", "uka" -> role = Role.LITTLE_BRO;
            default -> {
                return;
            }
        }
        if (!isMafiaRole(role)) {
            return;
        }

        Set<Long> recipients = new HashSet<>();
        recipients.addAll(lobby.getPlayersByRole(Role.MAFIA));
        recipients.addAll(lobby.getPlayersByRole(Role.DON));
        recipients.addAll(lobby.getPlayersByRole(Role.BIG_BRO));
        recipients.addAll(lobby.getPlayersByRole(Role.LITTLE_BRO));

        recipients.retainAll(lobby.getAlivePlayers());
        recipients.remove(senderId);

        if (recipients.isEmpty()) {
            return;
        }

        String senderName;
        senderName = getCleanFirstName(senderId, lobby.getChatId());

        String roleName = switch (role) {
            case DON -> "Don";
            case BIG_BRO -> "Katta aka";
            case LITTLE_BRO -> "Uka";
            case MAFIA -> "Oddiy mafiya";
            default -> "Mafiya";
        };

        String finalMessage = "💬 " + roleName + " " + senderName + ": " + text;

        for (Long mafiaId : recipients) {
            sendSafe(mafiaId, finalMessage);
        }
    }

    private void sendSafe(Long chatId, String text) {
        try {
            sendMessage(chatId, text);
        } catch (TelegramApiException e) {
            System.err.println("❌ Xabar yuborishda xatolik: " + e.getMessage());
        }
    }

    public String getCleanFirstName(Long userId, Long chatId) {
        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatId.toString());
            getChatMember.setUserId(userId);

            ChatMember chatMember = execute(getChatMember);
            String name = chatMember.getUser().getFirstName();

            if (name == null || name.isBlank()) {
                return "Ishtirokchi";
            }

            name = name.replace("⭐️", "").replace("★", "").trim();

            if (name.startsWith("**") && name.endsWith("**")) {
                name = name.substring(2, name.length() - 2);
            } else if (name.startsWith("*") && name.endsWith("*")) {
                name = name.substring(1, name.length() - 1);
            }

            return name;
        } catch (Exception e) {
            System.out.println("⚠️ GetChatMember xatolik → userId: " + userId + " | chatId: " + chatId);
            return "Ishtirokchi";
        }
    }

    public String getUserMention(Long userId, Long groupId) {
        String name = getCleanFirstName(userId, groupId);
        return String.format("[%s](tg://user?id=%s)", name, userId);
    }

    public void day(long groupId, int nightCount) {
        try {

            String caption = "Xayrli tong\uD83C\uDF1D \n" +
                    "\uD83C\uDF04Kun: " + nightCount + "\n" +
                    "Shamollar tundagi mish-mishlarni butun shaharga yetkazmoqda..";

            SendAnimation sendAnimation = SendAnimation.builder()
                    .chatId(String.valueOf(groupId))
                    .animation(new InputFile("BAACAgEAAxkBAAJHE2jlgU_gXxsSNZQ5s4KqBTU0sMKtAAK8BgAChq7ARAjY91TauajxNgQ")) // <-- GIF fayl yo‘li
                    .caption(caption)
                    .parseMode("Markdown")
                    .replyMarkup(getBotButton())
                    .build();

            execute(sendAnimation);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();

        StringBuilder sb = new StringBuilder("*Tirik o'yinchilar:*\n");
        Map<String, Integer> roleCounts = new HashMap<>();

        int count = 1;
        for (Long playerId : alivePlayers) {
            sb.append(count++)
                    .append(". ").append(getUserMention(playerId, groupId)).append("\n");

            Role role = lobby.getPlayerRoles().get(playerId);
            if (role != null) {
                roleCounts.put(role.getDisplayName(), roleCounts.getOrDefault(role.getDisplayName(), 0) + 1);
            }

            if (role == null) {
                System.out.println("⚠️ [" + groupId + "] O‘yinchi " + playerId + " rolsiz qoldi (chiqib ketgan yoki roli belgilanmagan).");
                continue;
            }
        }
        sb.append("\n*Ulardan:* \n");

        int totalRoles = roleCounts.size();
        int current = 0;

        for (Map.Entry<String, Integer> entry : roleCounts.entrySet()) {
            String roleName = entry.getKey();
            int countPlayers = entry.getValue();

            if (countPlayers > 1) {
                sb.append(roleName).append(" - ").append(countPlayers).append(" ta");
            } else {
                sb.append(roleName);
            }

            // Agar oxirgi emas bo‘lsa, vergul qo‘shamiz
            current++;
            if (current < totalRoles) {
                sb.append(", ");
            }
        }

        sb.append("\n*Jami:* ").append(alivePlayers.size());
        try {
            SendMessage msg = SendMessage.builder()
                    .chatId(String.valueOf(groupId))
                    .text(sb.toString())
                    .parseMode("Markdown")
                    .build();
            execute(msg);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        timerManager.startTimerForGroup(groupId, "Kun", () -> {
            try {
                if (checkGameOver(groupId)) {
                    handleDonDeath(groupId);
                    vote(groupId);
                }
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void night(long groupId) throws TelegramApiException {
        nightClosed.remove(groupId);
        nightClosed.put(groupId, false);

        String caption = "🌙 🌃 Tun\n" +
                "Ko'chaga faqat jasur va qo'rqmas odamlar chiqishdi. Ertalab tirik qolganlarni sanaymiz...";

        SendAnimation sendAnimation = SendAnimation.builder()
                .chatId(String.valueOf(groupId))
                .animation(new InputFile("BAACAgEAAxkBAAJHFWjlgYcVTjx09y3b0vt6rOLsOQl5AAIHBgAC30VpRGVplyBG-CAeNgQ")) // GIF
                .caption(caption)
                .replyMarkup(getBotButton())
                .parseMode("Markdown")
                .build();

        execute(sendAnimation);

        GameLobby lobby = lobbyManager.getLobby(groupId);
        lobby.clearProtections();

        Set<Long> alivePlayers = lobby.getAlivePlayers();
        Map<Long, Role> playerRoles = lobby.getPlayerRoles();
        for (Long playerId : alivePlayers) {
            if (lobby.isSleeping(playerId)) continue;

            lobby.wakeUpPlayers();
            Role role = playerRoles.get(playerId);
            if (role == null) continue;

            switch (role) {
                case DON -> sendDon(playerId, groupId, lobby.getGameId());
                case SHERIF -> sendKom(playerId, groupId, lobby.getGameId());
                case DOCTOR -> sendDoc(playerId, groupId, lobby.getGameId());
                case MAFIA -> sendMafiaVoteMenu(playerId, groupId, lobby.getGameId());
                case SLEEPWALKER -> sendKezuvchi(playerId, groupId, lobby.getGameId());
                case KILLER -> sendQotil(playerId, groupId, lobby.getGameId());
                case GUARD -> sendQoravul(playerId, groupId, lobby.getGameId());
                case BIG_BRO -> sendBIGBRO(playerId, groupId, lobby.getGameId());
                case LITTLE_BRO -> sendLITTLEBRO(playerId, groupId, lobby.getGameId());
            }
        }

        StringBuilder sb = new StringBuilder("*Tirik o'yinchilar:*\n");
        Map<String, Integer> roleCounts = new HashMap<>();
        int count = 1;

        for (Long playerId : alivePlayers) {
            sb.append(count++).append(". ").append(getUserMention(playerId, groupId)).append("\n");
            Role role = lobby.getPlayerRoles().get(playerId);
            if (role != null) {
                roleCounts.put(role.getDisplayName(), roleCounts.getOrDefault(role.getDisplayName(), 0) + 1);
            }
        }
        sb.append("\n*Ulardan:* \n");

        int totalRoles = roleCounts.size();
        int current = 0;

        for (Map.Entry<String, Integer> entry : roleCounts.entrySet()) {
            String roleName = entry.getKey();
            int countPlayers = entry.getValue();

            if (countPlayers > 1) {
                sb.append(roleName).append(" - ").append(countPlayers).append(" ta");
            } else {
                sb.append(roleName);
            }

            // Agar oxirgi emas bo‘lsa, vergul qo‘shamiz
            current++;
            if (current < totalRoles) {
                sb.append(", ");
            }
        }
        sb.append("\n*Jami:* ").append(alivePlayers.size());
        execute(SendMessage.builder()
                .chatId(String.valueOf(groupId))
                .text(sb.toString())
                .parseMode("Markdown")
                .build());

        timerManager.startTimerForGroup(groupId, "Tun", () -> {
            nightClosed.put(groupId, true);
            try {
                sendNightActions(groupId);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
            int nightCount = nightCountMap.getOrDefault(groupId, 0) + 1;
            nightCountMap.put(groupId, nightCount);
            try {
                checkNightInactivity(groupId);
                if (checkGameOver(groupId)) {
                    handleDonDeath(groupId);
                    day(groupId, nightCount);
                }
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void checkNightInactivity(Long groupId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        if (lobby == null) return;

        Map<Long, Integer> inactivityCount = lobby.getInactivityCount();
        if (inactivityCount == null) {
            inactivityCount = new ConcurrentHashMap<>();
            lobby.setInactivityCount(inactivityCount);
        }

        Set<Long> alivePlayers = new HashSet<>(lobby.getAlivePlayers());
        List<Long> toRemove = new ArrayList<>();

        for (Long playerId : alivePlayers) {
            Role role = lobby.getPlayerRoleEnum(playerId);
            if (role == null) continue;

            switch (role) {
                // faqat tunlik harakat qilishi kerak bo‘lgan rollar
                case DON, SHERIF, DOCTOR, SLEEPWALKER, KILLER, GUARD, BIG_BRO, LITTLE_BRO -> {

                    if (!inactivityCount.containsKey(playerId)) {
                        // ❌ Bu tun hech qanday action qilmagan (callback orqali 0 bo‘lmagan)
                        inactivityCount.put(playerId, 1);
                        System.out.println("⚠️ Player " + playerId + " birinchi marta inaktiv (hisob 1).");

                    } else {
                        int count = inactivityCount.get(playerId);

                        if (count == 0) {
                            // ✅ callback orqali action qilgan → hisob 0 saqlanadi
                            System.out.println("✅ Player " + playerId + " action qildi (hisob 0).");

                        } else {
                            // ❌ oldin ham inaktiv bo‘lgan → yana +1
                            count++;
                            inactivityCount.put(playerId, count);
                            System.out.println("⚠️ Player " + playerId + " inaktivlik darajasi: " + count);

                            if (count >= 3) {
                                lobby.killPlayer(playerId);
                                toRemove.add(playerId);

                                try {
                                    GetChatMember getChatMember = new GetChatMember();
                                    getChatMember.setChatId(groupId.toString());
                                    getChatMember.setUserId(playerId);

                                    ChatMember chatMember = execute(getChatMember);
                                    String playerName = chatMember.getUser().getFirstName();
                                    String playerLink = "<a href=\"tg://user?id=" + playerId + "\">" + playerName + "</a>";

                                    String deathMsg = "Aholidan kimdir " + role.getDisplayName() + " " + playerLink + " o‘limidan oldin:\n" +
                                            "Men o'yin paytida boshqa uxlamayma-a-a-a-a-a-a-an! deb qichqirganini eshitgan.";

                                    SendMessage msg = SendMessage.builder()
                                            .chatId(groupId)
                                            .text(deathMsg)
                                            .parseMode("HTML")
                                            .build();

                                    execute(msg);

                                } catch (TelegramApiException ex) {
                                    System.out.println("❌ ChatMember yoki xabar yuborishda xato: " + ex.getMessage());
                                }
                            }
                        }
                    }
                }
                default -> {
                    // boshqa rollarga inaktivlik yuritilmaydi
                }
            }
        }

        // o‘ldirilganlarni hisobdan olib tashlaymiz
        for (Long playerId : toRemove) {
            inactivityCount.remove(playerId);
        }
        callbackDataMap.remove(groupId);
    }

    public void sendNightActions(long groupId) throws TelegramApiException {
        List<Map<String, String>> dataList = callbackDataMap.get(groupId);
        if (dataList == null || dataList.isEmpty()) {
            sendMessage(groupId, "🌙 Tunda hech kim o'lmadi!");
            return;
        }
        String bigBroTarget = null;
        String littleBroTarget = null;
        Map<String, List<String>> attackerPrefixes = new HashMap<>();
        Map<String, String> savedByDoctor = new HashMap<>();
        Map<String, String> doctorNames = new HashMap<>();

        for (Map<String, String> data : dataList) {
            String action = data.get("action");
            String otherPlayerId = data.get("otherPlayerId");
            String prefix = data.get("prefix");
            if ("hlth".equals(action) && "doc".equals(prefix)) {
                Long doctorId = Long.parseLong(data.get("playerId"));
                Long targetId = Long.parseLong(otherPlayerId);
                if (doctorId.equals(targetId)) {
                    doctorSelfUsed
                            .computeIfAbsent(groupId, k -> new HashSet<>())
                            .add(doctorId);
                }
                docHealed(groupId, doctorId, targetId);
                savedByDoctor.put(otherPlayerId, String.valueOf(doctorId));
                doctorNames.put(String.valueOf(doctorId), getCleanFirstName(doctorId, groupId));
                continue;
            }

            if ("check".equals(action) && "kam".equals(prefix)) {
                GameLobby lobby = lobbyManager.getLobby(groupId);
                Long targetId = Long.valueOf(otherPlayerId);

                String checkedRole = String.valueOf(lobby.getPlayerRole(targetId));
                String message = String.format(
                        "%s - %s",
                        getUserMention(targetId, groupId),
                        checkedRole
                );

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(data.get("playerId")); // kamissar
                sendMessage.setText(message);
                sendMessage.setParseMode("Markdown");
                execute(sendMessage);

                SendMessage notifyTarget = new SendMessage();
                notifyTarget.setChatId(targetId.toString());
                notifyTarget.setText("Kimdir sizning rolingizga judayam qiziqdi..!");
                notifyTarget.setParseMode("Markdown");
                execute(notifyTarget);
                continue;
            }

            if ("kez".equals(prefix)) {
                Long kezId = Long.parseLong(data.get("playerId"));
                Long targetId = Long.parseLong(otherPlayerId);
                handleKezuvchiAction(groupId, otherPlayerId);
                kezHealed(groupId, kezId, targetId);
                continue;
            }
            if ("BIGBRO".equals(prefix)) {
                bigBroTarget = otherPlayerId;
            }
            if ("LITTLEBRO".equals(prefix)) {
                littleBroTarget = otherPlayerId;
            }
            if ("qor".equals(prefix)) {
                GameLobby lobby = lobbyManager.getLobby(groupId);
                lobby.protectPlayer(Long.parseLong(otherPlayerId));
                Long qorId = Long.parseLong(data.get("playerId"));
                qorHealed(groupId, qorId, Long.valueOf(otherPlayerId));
                continue;
            }

            if ("qotil".equals(prefix)) {
                attackerPrefixes.computeIfAbsent(otherPlayerId, k -> new ArrayList<>()).add(prefix);
                continue;
            }
            if ("kill".equals(action)) {
                attackerPrefixes.computeIfAbsent(otherPlayerId, k -> new ArrayList<>()).add(prefix);
            }
        }
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        boolean bigBroAlive = lobby.getPlayersByRole(Role.BIG_BRO).stream().anyMatch(alivePlayers::contains);
        boolean littleBroAlive = lobby.getPlayersByRole(Role.LITTLE_BRO).stream().anyMatch(alivePlayers::contains);

        if (bigBroAlive && littleBroAlive) {
            if (bigBroTarget != null && littleBroTarget != null && bigBroTarget.equals(littleBroTarget)) {
                attackerPrefixes.computeIfAbsent(bigBroTarget, k -> new ArrayList<>()).add("brothers");
                sendMessage(groupId, "🧑‍🦰👦 Aka va Uka bir xil odamni tanladi!");
                attackerPrefixes.entrySet().removeIf(entry -> entry.getValue().contains("don"));
            } else {
                sendMessage(groupId, "🧑‍🦰👦 Aka va Uka kelisha olmadi! Hech kim o‘ldirilmadi.");
                return;
            }
        }

        Set<String> attackedPlayers = attackerPrefixes.keySet();
        Set<String> actuallyKilled = new HashSet<>();

        for (Map.Entry<String, List<String>> entry : attackerPrefixes.entrySet()) {
            String otherPlayerId = entry.getKey();
            List<String> prefixes = entry.getValue();
            Long targetId = Long.parseLong(otherPlayerId);
            Role targetRole = lobby.getPlayerRoles().get(targetId);
            if (targetRole == Role.WOLF) {
                if (prefixes.contains("don")) {
                    lobby.changePlayerRole(targetId, Role.MAFIA);
                    sendMessage(groupId, "🐺 Bo‘ri mafiaga aylandi!");
                    continue;
                } else if (prefixes.contains("kam")) {
                    lobby.changePlayerRole(targetId, Role.SERGEANT);
                    sendMessage(groupId, "🐺 Bo‘ri Serjantga aylandi!");
                    continue;
                }
            }
            if (savedByDoctor.containsKey(otherPlayerId)) {
                handleDoctorActions(
                        otherPlayerId,
                        savedByDoctor.get(otherPlayerId),
                        prefixes,
                        groupId,
                        true
                );
                continue;
            }
            String role = String.valueOf(lobby.getPlayerRole(targetId));
            boolean wasProtected = false;
            boolean killedByKiller = prefixes.contains("qotil");

            Integer killerShieldQuantity = Database.getActiveItemQuantity(targetId, "killerShield");
            Integer shieldQuantity = Database.getActiveItemQuantity(targetId, "shield");

            if (killedByKiller) {
                if (killerShieldQuantity != null && killerShieldQuantity > 0) {
                    decrementItemQuantity(targetId, "killerShield");
                    wasProtected = true;
                } else if (shieldQuantity != null && shieldQuantity > 0) {
                    decrementItemQuantity(targetId, "shield");
                    wasProtected = true;
                }
            } else {
                if (shieldQuantity != null && shieldQuantity > 0) {
                    decrementItemQuantity(targetId, "shield");
                    wasProtected = true;
                }
            }
            if (wasProtected) {
                sendMessage(groupId, "🛡 Kimdir ximoyasini ishlatdi");
                continue;
            }

            actuallyKilled.add(otherPlayerId);
            lobby.killPlayer(targetId);
            sendLastWordPrompt(targetId, groupId);

            String attackers = prefixes.stream()
                    .distinct()
                    .map(prefix -> switch (prefix) {
                        case "qotil" -> "🔪 Qotil";
                        case "kam" -> "🕵🏻‍♂ Komissar Katani";
                        case "don" -> "\uD83E\uDD35\uD83C\uDFFB Don";
                        case "mafia" -> "🤵🏼 Mafiya";
                        case "brothers" -> "🧑‍🦰👦 Aka va Uka";
                        default -> null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            String message = String.format(
                    "Tunda *%s* - [%s](tg://user?id=%s) shavqatsizlarcha o‘ldirildi.\n" +
                            "Aytishlaricha, unikiga *%s* kelgan.",
                    role,
                    getCleanFirstName(targetId, groupId),
                    otherPlayerId,
                    attackers
            );

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(groupId));
            sendMessage.setText(message);
            sendMessage.setParseMode("Markdown");
            execute(sendMessage);
        }

        for (Map.Entry<String, String> saved : savedByDoctor.entrySet()) {
            String savedId = saved.getKey();
            String doctorId = saved.getValue();

            if (!attackedPlayers.contains(savedId)) {
                handleDoctorActions(
                        savedId,
                        doctorId,
                        null,
                        groupId,
                        false
                );
            }
        }
        if (actuallyKilled.isEmpty()) {
            sendMessage(groupId, "🌑 Shaharda sintatsiya, negadir bu kecha hech kim o‘lmadi…");
        }
        boolean commissarKilled = false;
        for (String killedId : actuallyKilled) {
            Long killedLong = Long.parseLong(killedId);
            Role killedRole = lobby.getPlayerRoles().get(killedLong);
            if (killedRole == Role.SHERIF) {
                commissarKilled = true;
                break;
            }
        }

        if (commissarKilled) {
            List<Long> sergeants = lobby.getPlayersByRole(Role.SERGEANT)
                    .stream()
                    .filter(lobby::isAlive)
                    .toList();

            if (!sergeants.isEmpty()) {
                Long newCommissar = sergeants.get(new Random().nextInt(sergeants.size()));
                lobby.changePlayerRole(newCommissar, Role.SHERIF);
                sendMessage(newCommissar, "🕵️ Endi siz yangi Komissarsiz!");
            }
        }
        callbackDataMap.remove(groupId);
    }

    private void sendLastWordPrompt(Long playerId, Long groupId) throws TelegramApiException {
        SendMessage lastWordMsg = new SendMessage();
        lastWordMsg.setChatId(String.valueOf(playerId));
        lastWordMsg.setText("Sizni shavqatsizlarcha o‘ldirishdi.\n" +
                "Oxirgi so‘zingizni yozishingiz mumkin)..");
        execute(lastWordMsg);
        lastWordsWaiting.computeIfAbsent(groupId, k -> new HashSet<>()).add(playerId);

        timerManager.startTimerForGroup(groupId, "So'ngi sozni aytish vaqti", () -> {
            lastWordsWaiting.remove(groupId);
            System.out.println("⏰ Timer tugadi, groupId = " + groupId + " uchun oxirgi so'z bekor qilindi");
        });
    }

    private void handleDoctorActions(
            String savedPlayerId,
            String doctorId,
            List<String> attackerPrefixes,
            long groupId,
            boolean wasAttacked
    ) throws TelegramApiException {
        if (wasAttacked) {
            String attackers = attackerPrefixes.stream().map(prefix -> switch (prefix) {
                case "qotil" -> "🔪 Qotil";
                case "kam" -> "🕵🏻‍♂ Kamissar Katani";
                case "don" -> "\uD83E\uDD35\uD83C\uDFFB Don";
                default -> "Nomaʼlum";
            }).collect(Collectors.joining(", "));
            SendMessage msgToSaved = new SendMessage();
            msgToSaved.setChatId(savedPlayerId);
            msgToSaved.setText("Doktor sizni davoladi :)\n" +
                    "Sizni " + attackers + " o‘ldirmoqchi bo‘lgandi.");
            execute(msgToSaved);
            String docMessage = String.format(
                    "Siz - %s ni davoladingiz :)\nUni mehmoni %s edi.",
                    getCleanFirstName(Long.valueOf(savedPlayerId), groupId),
                    attackers
            );
            SendMessage msgToDoctor = new SendMessage();
            msgToDoctor.setChatId(doctorId);
            msgToDoctor.setText(docMessage);
            execute(msgToDoctor);

        } else {
            SendMessage msgToSaved = new SendMessage();
            msgToSaved.setChatId(savedPlayerId);
            msgToSaved.setText("Doktor sizning uyingizga mehmon bo‘ldi :)");
            execute(msgToSaved);

            SendMessage msgToDoctor = new SendMessage();
            msgToDoctor.setChatId(doctorId);
            msgToDoctor.setText("Doktor yordam bera olmadi :)");
            execute(msgToDoctor);
        }
    }

    private void handleKezuvchiAction(long groupId, String targetPlayerId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        lobby.setSleeping(Long.parseLong(targetPlayerId));
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(targetPlayerId);
        sendMessage.setText("Kezuvchi siznikiga mehmonga keldi!");
        execute(sendMessage);
    }

    private final Map<Long, Map<Long, Integer>> lastActionNight = new ConcurrentHashMap<>();

    public void manageActions(long groupId, String action, String prefix, String otherPlayerId, Long playerId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        int currentNight = nightCountMap.getOrDefault(groupId, 0);
        Map<Long, Integer> groupLastActions = lastActionNight.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>());

        Integer lastNight = groupLastActions.get(playerId);
        if (lastNight != null && lastNight == currentNight) {
            return;
        }
        groupLastActions.put(playerId, currentNight);

        if ("kam".equalsIgnoreCase(prefix)) {
            String groupMessage;
            String sergeantMessage;
            Long targetId = Long.parseLong(otherPlayerId);

            if ("check".equalsIgnoreCase(action)) {
                groupMessage = "🕵️ Kamissar xizmatda!";
                sergeantMessage = "🕵️ Kamissar " + getUserMention(targetId, groupId) + " ni tekshirdi!";
            } else {
                groupMessage = "🕵️ Kamissar qurolini o‘qladi!";
                sergeantMessage = "🕵️ Kamissar " + getUserMention(targetId, groupId) + " ni o‘ldirdi!";
            }
            sendMessage(groupId, groupMessage);
            List<Long> sergeants = lobby.getPlayersByRole(Role.SERGEANT);
            for (Long sergeantId : sergeants) {
                sendMessage(sergeantId, sergeantMessage);
            }
        } else if ("don".equalsIgnoreCase(prefix)) {
            sendMessage(groupId, "Don navbatdagi o‘ljasini tanladi!");
            sendMafiaAction(groupId, otherPlayerId, playerId);
        } else if ("doc".equalsIgnoreCase(prefix)) {
            sendMessage(groupId, "Doktor tungi navbatchilikka ketdi!");
        } else if ("mafia".equalsIgnoreCase(prefix)) {
            sendMafiaAction(groupId, otherPlayerId, playerId);
            return;
        } else if ("kez".equalsIgnoreCase(prefix)) {
            sendMessage(groupId, "Kezuvchi mehmon bo‘ldi...");
        } else if ("qotil".equalsIgnoreCase(prefix)) {
            sendMessage(groupId, "Qotilning qo‘lida qilich bor.");
        } else if ("qor".equalsIgnoreCase(prefix)) {
            sendMessage(groupId, "🛡 Qarovul tungi xizmatchilikda.");
        }

        Map<String, String> data = Map.of(
                "action", action,
                "prefix", prefix,
                "otherPlayerId", otherPlayerId,
                "playerId", String.valueOf(playerId)
        );
        callbackDataMap
                .computeIfAbsent(groupId, k -> new ArrayList<>())
                .add(data);
    }

    public void addVote(Long groupId, String targetId) {
        Map<String, Integer> groupVotes = voteCountMap.computeIfAbsent(groupId, k -> new HashMap<>());
        groupVotes.put(targetId, groupVotes.getOrDefault(targetId, 0) + 1);
    }

    public String getTopVotedTargetName(Long groupId) {
        Map<String, Integer> groupVotes = voteCountMap.get(groupId);
        if (groupVotes == null || groupVotes.isEmpty()) return null;

        String topTarget = null;
        int maxVotes = -1;
        boolean tie = false;

        for (Map.Entry<String, Integer> entry : groupVotes.entrySet()) {
            int votes = entry.getValue();
            if (votes > maxVotes) {
                maxVotes = votes;
                topTarget = entry.getKey();
                tie = false;
            } else if (votes == maxVotes) {
                tie = true;
            }
        }
        return tie ? null : topTarget;
    }

    public void vote(long groupId) {
        voteClosed.remove(groupId);
        voteCountMap.remove(groupId);
        voteClosed.put(groupId, false);

        String text = "Aybdorlarni aniqlash va jazolash vaqti keldi\nOvoz berish uchun 45 sekund";
        InlineKeyboardButton voteButton = new InlineKeyboardButton();
        voteButton.setText("Ovoz berish");
        voteButton.setUrl("https://t.me/" + getBotUsername()); // URL o'rniga callback data

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(voteButton);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);

        SendMessage message = SendMessage.builder()
                .chatId(groupId)
                .text(text)
                .replyMarkup(markup)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();

        for (Long playerId : alivePlayers) {
            if (lobby.isSleeping(playerId)) {
                continue;
            }
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            Role playerRole = lobby.getPlayerRoles().get(playerId);

            for (Long targetId : alivePlayers) {
                if (targetId.equals(playerId)) continue;

                InlineKeyboardButton btn = new InlineKeyboardButton();
                String targetName = getCleanFirstName(targetId, groupId);
                Role targetRole = lobby.getPlayerRoles().get(targetId);
                if (isMafiaRole(playerRole) && isMafiaRole(targetRole)) {
                    btn.setText("\uD83E\uDD35\uD83C\uDFFB - " + targetName);
                } else {
                    btn.setText(targetName);
                }

                btn.setCallbackData("w_vote_" + targetId + "_" + groupId + "_" + lobby.getGameId() + "_" + playerId);
                List<InlineKeyboardButton> rowBtn = new ArrayList<>();
                rowBtn.add(btn);
                buttons.add(rowBtn);
            }

            // "Otkazib yuborish" tugmasini qo'shish
            InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                    .text("Otkazib yuborish")
                    .callbackData("skip_vote_" + playerId + "_" + groupId + "_" + (playerRole != null ? playerRole.name() : "UNKNOWN"))
                    .build();
            List<InlineKeyboardButton> skipRow = new ArrayList<>();
            skipRow.add(skipButton);
            buttons.add(skipRow);

            InlineKeyboardMarkup playerMarkup = new InlineKeyboardMarkup();
            playerMarkup.setKeyboard(buttons);

            SendMessage privateMsg = SendMessage.builder()
                    .chatId(String.valueOf(playerId))
                    .text("🪓 Kimni osamiz?")
                    .replyMarkup(playerMarkup)
                    .build();
            try {
                execute(privateMsg);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        timerManager.startTimerForGroup(groupId, "Vote", () -> {
            voteClosed.put(groupId, true);
            try {
                handleDonDeath(groupId);
                accept(groupId, getTopVotedTargetName(groupId));
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private final Map<Long, Object> groupLocks = new ConcurrentHashMap<>();

    private final Map<Long, Set<Long>> votesPerDay = new ConcurrentHashMap<>();

    public void voteMessage(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        String[] parts = data.split("_");
        Long groupId = Long.parseLong(parts[3]);
        groupLocks.remove(groupId);
        votesPerDay.remove(groupId);
        Object lock = groupLocks.computeIfAbsent(groupId, k -> new Object());

        synchronized (lock) {
            if (!voteClosed.get(groupId)) {
                Long targetId = Long.parseLong(parts[2]);
                long playerId = Long.parseLong(parts[5]);
                Set<Long> votedPlayers = votesPerDay.computeIfAbsent(groupId, k -> new HashSet<>());
                if (votedPlayers.contains(playerId)) {
                    return;
                }
                votedPlayers.add(playerId);

                GameLobby lobby = lobbyManager.getLobby(groupId);
                String playerRole = String.valueOf(lobby.getPlayerRole(playerId));

                String groupText;
                if ("JANOB".equalsIgnoreCase(playerRole)) {
                    groupText = "🎩 Janob - " + getUserMention(targetId, groupId) + " ga ovoz berdi.";
                    addVote(groupId, String.valueOf(targetId));
                    addVote(groupId, String.valueOf(targetId));
                } else {
                    groupText = getUserMention(playerId, groupId) + " - " + getUserMention(targetId, groupId) + " ga ovoz berdi.";
                    addVote(groupId, String.valueOf(targetId));
                }
                SendMessage groupMessage = SendMessage.builder()
                        .chatId(String.valueOf(groupId))
                        .text(groupText)
                        .parseMode("Markdown")
                        .build();
                try {
                    execute(groupMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                // Foydalanuvchiga ovoz berilganini ko‘rsatish
                String editedText = "Siz " + getCleanFirstName(targetId, groupId) + " ni tanladingiz.";
                EditMessageText editMessage = EditMessageText.builder()
                        .chatId(String.valueOf(callbackQuery.getMessage().getChatId()))
                        .messageId(callbackQuery.getMessage().getMessageId())
                        .text(editedText)
                        .parseMode("Markdown")
                        .replyMarkup(backToGroupButton(groupId))
                        .build();
                try {
                    execute(editMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void accept(long groupId, String targetId) throws TelegramApiException {
        boolean gameOver = true;

        if (targetId == null || targetId.trim().isEmpty()) {
            String text = "Ovoz berish yakunlandi:\n" +
                    "Aholi kelisha olmadi... Kelisha olmaslik oqibatida hech kim osilmadi...";
            sendMessage(groupId, text);
            if (gameOver) {
                handleDonDeath(groupId);
                night(groupId);
            }
            return;
        }
        String targetMention = getUserMention(Long.valueOf(targetId), groupId);
        String text = "Rostdan ham " + targetMention + " ni osmoqchimisiz?";
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(groupId));
        message.setText(text);
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();

        InlineKeyboardButton yesBtn = new InlineKeyboardButton();
        yesBtn.setText("👍 0");
        yesBtn.setCallbackData("vote_yes_" + targetId);

        InlineKeyboardButton noBtn = new InlineKeyboardButton();
        noBtn.setText("👎 0");
        noBtn.setCallbackData("vote_no_" + targetId);

        List<InlineKeyboardButton> row = Arrays.asList(yesBtn, noBtn);
        markup.setKeyboard(Collections.singletonList(row));

        message.setReplyMarkup(markup);

        Message sentMsg = execute(message);

        timerManager.startTimerForGroup(groupId, "Tasdiqlash", () -> {
            boolean gameOverr = true;
            try {
                Map<String, Set<Long>> yesVotes = yesVotesMap.getOrDefault(groupId, new HashMap<>());
                Map<String, Set<Long>> noVotes = noVotesMap.getOrDefault(groupId, new HashMap<>());

                int yesCount = yesVotes.getOrDefault(targetId, Set.of()).size();
                int noCount = noVotes.getOrDefault(targetId, Set.of()).size();
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(String.valueOf(groupId));
                deleteMessage.setMessageId(sentMsg.getMessageId());
                execute(deleteMessage);

                String resultText;
                if (yesCount > noCount) {
                    GameLobby lobby = lobbyManager.getLobby(groupId);
                    String role = String.valueOf(lobby.getPlayerRole(Long.valueOf(targetId)));
                    Integer voteShieldQty = Database.getActiveItemQuantity(Long.valueOf(targetId), "voteshield");
                    if (voteShieldQty != null && voteShieldQty > 0) {
                        Database.decrementItemQuantity(Long.valueOf(targetId), "voteshield");

                        String shieldMsg = String.format(
                                "Ovoz natijasi: %d 👍  |  %d 👎\n\n%s himoyasidan foydalandi!",
                                yesCount, noCount, getUserMention(Long.parseLong(targetId), groupId)
                        );
                        sendMessage(groupId, shieldMsg);
                        yesVotesMap.remove(groupId);
                        noVotesMap.remove(groupId);
                    } else {
                        if (lobby.isProtected(Long.valueOf(targetId))) {
                            resultText = String.format(
                                    "Ovoz berish natijalari:\n%d 👍  |  %d 👎\n\n" +
                                            "%s ni osishga qaror qilishdi.\n" +
                                            "🛡 Lekin Qarovul uni himoya qilgani uchun osilmadi!",
                                    yesCount, noCount, getUserMention(Long.parseLong(targetId), groupId)
                            );
                            sendMessage(groupId, resultText);
                        } else {
                            lobby.killPlayer(Long.valueOf(targetId));
                            sendLastWordPrompt(Long.valueOf(targetId), groupId);

                            if ("\uD83E\uDD26\uD83C\uDFFC Suidsid".equalsIgnoreCase(role)) {
                                lobby.addSuicidedPlayer(Long.valueOf(targetId));
                                System.out.println("💀 Suidsid kunduzi o‘zini osdi, saqlandi: " + targetId);
                            }

                            if ("Tulki".equalsIgnoreCase(role)) {
                                Set<Long> voters = yesVotes.getOrDefault(targetId, Set.of());
                                if (!voters.isEmpty()) {
                                    Long firstVoter = voters.iterator().next();
                                    if (lobby.isAlive(firstVoter)) {
                                        lobby.killPlayer(firstVoter);
                                        sendLastWordPrompt(firstVoter, groupId);
                                        sendMessage(groupId,
                                                "🦊 Tulki osildi! Lekin unga birinchi ovoz bergan " +
                                                        getUserMention(firstVoter, groupId) + " ham o‘ldi!");
                                    }
                                }
                            }
                            if ("LIDER".equalsIgnoreCase(role)) {
                                int mafiaCount = 0;
                                int townCount = 0;

                                Set<Long> voters = yesVotes.getOrDefault(targetId, Set.of());
                                for (Long voterId : voters) {
                                    String voterRole = String.valueOf(lobby.getPlayerRole(voterId));

                                    if (voterRole.equalsIgnoreCase("\uD83E\uDD35\uD83C\uDFFC Mafiya")
                                            || voterRole.equalsIgnoreCase("\uD83E\uDD35\uD83C\uDFFB Don")
                                            || voterRole.equalsIgnoreCase("Uka")
                                            || voterRole.equalsIgnoreCase("Aka")
                                            || voterRole.equalsIgnoreCase("\uD83D\uDC68\u200D\uD83D\uDCBC Advokat")) {
                                        mafiaCount++;
                                    } else if (voterRole.equalsIgnoreCase("Darbadar")
                                            || voterRole.equalsIgnoreCase("Lider")
                                            || voterRole.equalsIgnoreCase("Qarovul")
                                            || voterRole.equalsIgnoreCase("Podshoh")
                                            || voterRole.equalsIgnoreCase("\uD83E\uDD26\uD83C\uDFFC Suidsid")
                                            || voterRole.equalsIgnoreCase("\uD83D\uDC6E\uD83C\uDFFB\u200D♂ Serjant")
                                            || voterRole.equalsIgnoreCase("\uD83D\uDC83 Kezuvchi")
                                            || voterRole.equalsIgnoreCase("\uD83D\uDC68\uD83C\uDFFB\u200D⚕ Doktor")
                                            || voterRole.equalsIgnoreCase("\uD83D\uDD75\uD83C\uDFFB\u200D♂ Komissar katani")
                                            || voterRole.equalsIgnoreCase("\uD83D\uDC68\uD83C\uDFFC Tinch aholi")) {
                                        townCount++;
                                    }
                                }
                                String extraInfo = String.format(
                                        "\nSizga:\n" +
                                                "%d ta - Mafiya\n" +
                                                "%d ta - Shaharliklar\n" +
                                                "Ovoz berdi.",
                                        mafiaCount, townCount
                                );
                                sendMessage(Long.valueOf(targetId), extraInfo);
                                resultText = String.format(
                                        "Ovoz berish natijalari:\n%d 👍  |  %d 👎\n\n" +
                                                getUserMention(Long.parseLong(targetId), groupId) +
                                                " kunduzgi yig‘ilishda osildi!\nU edi %s.",
                                        yesCount, noCount, role
                                );
                            } else {
                                resultText = String.format(
                                        "Ovoz berish natijalari:\n%d 👍  |  %d 👎\n\n" +
                                                getUserMention(Long.parseLong(targetId), groupId) +
                                                " kunduzgi yig‘ilishda osildi!\nU edi %s.",
                                        yesCount, noCount, role
                                );
                            }
                            sendMessage(groupId, resultText);
                            gameOverr = checkGameOver(groupId);
                        }
                    }
                    yesVotesMap.remove(groupId);
                    noVotesMap.remove(groupId);

                } else {
                    resultText = String.format(
                            "Ovoz berish natijalari:\n%d 👍  |  %d 👎\n\n" +
                                    "Axoli kelisha olmadi... Kelisha olmaslik oqibatida hech kim osilmadi...",
                            yesCount, noCount);
                    sendMessage(groupId, resultText);
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            try {
                if (gameOverr) {
                    handleDonDeath(groupId);
                    night(groupId);
                }
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void updateVoteButtons(CallbackQuery callbackQuery, String voteType, String targetId) throws TelegramApiException {
        long groupId = callbackQuery.getMessage().getChatId();
        long voterId = callbackQuery.getFrom().getId();

        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        if (!alivePlayers.contains(voterId) || String.valueOf(voterId).equals(targetId) || lobby.isSleeping(voterId)) {
            return;
        }

        Map<String, Set<Long>> yesVotes = yesVotesMap.computeIfAbsent(groupId, k -> new HashMap<>());
        Map<String, Set<Long>> noVotes = noVotesMap.computeIfAbsent(groupId, k -> new HashMap<>());

        yesVotes.computeIfAbsent(targetId, k -> new HashSet<>());
        noVotes.computeIfAbsent(targetId, k -> new HashSet<>());

        Set<Long> yesSet = yesVotes.get(targetId);
        Set<Long> noSet = noVotes.get(targetId);

        yesSet.remove(voterId);
        noSet.remove(voterId);

        if (voteType.equals("yes")) {
            yesSet.add(voterId);
        } else {
            noSet.add(voterId);
        }

        int yesCount = yesSet.size();
        int noCount = noSet.size();

        InlineKeyboardButton yesBtn = new InlineKeyboardButton("👍 " + yesCount);
        yesBtn.setCallbackData("vote_yes_" + targetId);

        InlineKeyboardButton noBtn = new InlineKeyboardButton("👎 " + noCount);
        noBtn.setCallbackData("vote_no_" + targetId);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(yesBtn, noBtn)));

        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(String.valueOf(groupId));
        editMarkup.setMessageId(callbackQuery.getMessage().getMessageId());
        editMarkup.setReplyMarkup(markup);

        execute(editMarkup);
    }

    private boolean checkGameOver(Long groupId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);

        if (lobby == null) {
            System.out.println("Lobby is null");
            return false;
        }

        Set<Long> alivePlayers = lobby.getAlivePlayers();
        Set<Long> allPlayers = lobby.getAllPlayers();

        Set<Long> aliveCitizens = new HashSet<>();
        Set<Long> aliveMafias = new HashSet<>();
        Set<Long> aliveSolo = new HashSet<>();

        for (Long playerId : alivePlayers) {
            Role role = lobby.getPlayerRoles().get(playerId);
            if (isCitizenRole(role)) {
                aliveCitizens.add(playerId);
            } else if (isMafiaRole(role)) {
                aliveMafias.add(playerId);
            } else {
                aliveSolo.add(playerId);
            }
        }

        boolean gameEnded = false;
        List<Long> winners = new ArrayList<>();

        // 🔹 Standart shartlar
        if (aliveMafias.size() == 1 && aliveCitizens.size() == 1 && aliveSolo.isEmpty()) {
            winners.addAll(aliveMafias);
            gameEnded = true;
        } else if (aliveCitizens.size() == 1 && aliveSolo.size() == 1 && aliveMafias.isEmpty()) {
            winners.addAll(aliveCitizens);
            gameEnded = true;
        } else if (aliveMafias.size() == 1 && aliveSolo.size() == 1 && aliveCitizens.isEmpty()) {
            winners.addAll(aliveMafias);
            gameEnded = true;
        } else if (aliveCitizens.isEmpty() && aliveMafias.isEmpty() && aliveSolo.size() == 1) {
            winners.addAll(aliveSolo);
            gameEnded = true;
        } else if (aliveMafias.isEmpty() && !aliveCitizens.isEmpty()) {
            winners.addAll(aliveCitizens);
            gameEnded = true;
        } else if (aliveCitizens.isEmpty() && !aliveMafias.isEmpty()) {
            winners.addAll(aliveMafias);
            gameEnded = true;
        } else if (aliveCitizens.size() == 1 && aliveMafias.isEmpty() && aliveSolo.isEmpty()) {
            winners.addAll(aliveCitizens);
            gameEnded = true;
        } else if (aliveMafias.size() == 1 && aliveCitizens.isEmpty() && aliveSolo.isEmpty()) {
            winners.addAll(aliveMafias);
            gameEnded = true;
        }

        // ✅ Suicided bo‘lganlarni ham g‘oliblar qatoriga qo‘shamiz (agar ularning jamoasi yutgan bo‘lsa)
        if (gameEnded) {
            for (Long playerId : allPlayers) {
                if (lobby.isSuicided(playerId)) {
                    if (!winners.contains(playerId)) {
                        winners.add(playerId);
                    }
                }
            }
        }

        if (gameEnded) {
            List<Long> losers = new ArrayList<>(allPlayers);
            losers.removeAll(winners);

            StringBuilder sb = new StringBuilder();
            sb.append("🎯 O‘yin tugadi!\n\n")
                    .append("G‘oliblar:\n");

            int index = 1;
            for (Long id : winners) {
                sb.append("    ").append(index++).append(". ")
                        .append(getUserMention(id, groupId)).append(" - ")
                        .append(lobby.getPlayerRole(id)).append("\n");
            }

            if (!losers.isEmpty()) {
                sb.append("\nQolgan o‘yinchilar:\n");
                for (Long id : losers) {
                    sb.append("    ").append(index++).append(". ")
                            .append(getUserMention(id, groupId)).append(" - ")
                            .append(lobby.getPlayerRole(id)).append("\n");
                }
            }

            long durationMillis = System.currentTimeMillis() - lobby.getStartTime();
            long minutes = durationMillis / 60000;
            sb.append("\n⏳ O‘yin: ").append(minutes).append(" minut davom etdi");

            sendMessage(lobby.getChatId(), sb.toString());
            for (Long id : winners) {
                addBalance(id, 50, 0);
                incrementWins(id);
                incrementGames(id);
                sendUserStatsWithButtons(id, "O‘yin tugadi!\nSizga 50-\uD83D\uDCB5 berildi!");
            }

            for (Long id : losers) {
                addBalance(id, 10, 0);
                incrementGames(id);
                sendUserStatsWithButtons(id, "O‘yin tugadi!\nSizga 10-\uD83D\uDCB5 berildi!");
            }
            lobbyManager.removeLobby(groupId);
            TimerManager.cancelTimerForGroup(groupId);
            voteCountMap.remove(groupId);
            yesVotesMap.remove(groupId);
            noVotesMap.remove(groupId);
            kezLastTarget.remove(groupId);
            doctorLastTarget.remove(groupId);
            qoravulLastTarget.remove(groupId);
            doctorSelfUsed.remove(groupId);
            pendingPurchases.remove(groupId);
            doctorSelfUsed.remove(groupId);
            lobby.inactivityCount.remove(groupId);
            nightCountMap.remove(groupId);
            lastWordsWaiting.remove(groupId);
            return false;
        }
        return true;
    }

    private boolean isCitizenRole(Role role) {
        if (role == null) return false;
        return switch (role) {
            case CITIZEN, SHERIF, DOCTOR, SLEEPWALKER, SERGEANT, SUICIDE,
                 KING, GUARD, LEADER -> true;
            default -> false;
        };
    }

    private boolean isMafiaRole(Role role) {
        if (role == null) return false;
        return switch (role) {
            case DON, BIG_BRO, LITTLE_BRO, MAFIA -> true;
            default -> false;
        };
    }

    public void startGame(GameLobby lobby, Long groupId) throws TelegramApiException {
        Set<Long> players = lobby.getPlayers();
        Map<Long, Role> playerRoles = RoleAssigner.assignRoles(players);
        lobby.setPlayerRoles(playerRoles);

        sendStartMessageWithButton(groupId);

        lobby.inactivityCount.remove(groupId);


        Set<Long> mafiaPlayers = new HashSet<>();
        for (Map.Entry<Long, Role> entry : playerRoles.entrySet()) {
            if (isMafiaRole(entry.getValue())) {
                mafiaPlayers.add(entry.getKey());
            }
        }

        Chat chat = execute(new GetChat(groupId.toString()));
        String groupLink;
        if (chat.getUserName() != null) {
            // public group
            groupLink = "https://t.me/" + chat.getUserName();
        } else {
            // private yoki supergroup
            groupLink = "https://t.me/c/" + String.valueOf(groupId).substring(4);
        }

        // Har bir o‘yinchiga rol yuboramiz
        for (Map.Entry<Long, Role> entry : playerRoles.entrySet()) {
            Long userId = entry.getKey();
            Role role = entry.getValue();

            String message = RoleDescription.roleMessages.get(role);

            // mafiya bo‘lsa sheriklari qo‘shiladi
            if (isMafiaRole(role)) {
                StringBuilder mafiaMsg = new StringBuilder(message);
                mafiaMsg.append("\n\n🤝 Sizning sheriklaringiz:\n");
                int idx = 1;
                for (Long mafiaId : mafiaPlayers) {
                    if (!mafiaId.equals(userId)) {
                        mafiaMsg.append("    ")
                                .append(idx++).append(". ")
                                .append(getUserMention(mafiaId, groupId))
                                .append(" - ").append(lobby.getPlayerRole(mafiaId))
                                .append("\n");
                    }
                }
                message = mafiaMsg.toString();
            }

            // 🔹 Rol + guruhga qaytish tugmasi
            InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                    .text("⬅️ Guruhga qaytish")
                    .url(groupLink)
                    .build();

            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                    .keyboard(List.of(List.of(backButton))).build();

            SendMessage sendMsg = SendMessage.builder()
                    .chatId(userId.toString())
                    .text(message)
                    .replyMarkup(markup)
                    .parseMode("Markdown")
                    .build();

            execute(sendMsg);
        }

        // O‘yin kechasi boshlanadi
        night(groupId);
    }

    public void handleAction(CallbackQuery callbackQuery) throws TelegramApiException {
        String[] parts = callbackQuery.getData().split("_");
        Long groupId = Long.valueOf(parts[3]);
        Long otherPlayerId = Long.valueOf(parts[5]);
        Long userId = Long.valueOf(parts[6]);
        String targetName = getCleanFirstName(otherPlayerId, groupId);
        String responseText = String.format("Siz *%s* ni tanladingiz.", targetName);

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(userId)
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(responseText)
                .replyMarkup(backToGroupButton(groupId))
                .parseMode("Markdown")
                .build();
        execute(editMessage);
    }

    public void KomissarAction(CallbackQuery callbackQuery) throws TelegramApiException {
        String[] parts = callbackQuery.getData().split("_");
        String action = parts[2];
        Long groupId = Long.valueOf(parts[3]);
        Long gameId = Long.valueOf(parts[4]);
        Long userId = Long.parseLong(parts[5]);
        System.out.println(action + " " + groupId + " " + gameId + " " + userId);

        String text = "check".equalsIgnoreCase(action)
                ? "Kimni tekshiramiz?"
                : "Kimni o'ldiramiz?";
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId)) continue;
            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_kam_" + action + "_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);
            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        EditMessageText edit = EditMessageText.builder()
                .chatId(userId)
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();
        execute(edit);
    }

    private void sendKom(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        InlineKeyboardButton checkButton = new InlineKeyboardButton();
        checkButton.setText("Tekshirish");
        checkButton.setCallbackData("w_kom_check_" + groupId + "_" + gameId + "_" + userId);
        InlineKeyboardButton killButton = new InlineKeyboardButton();
        killButton.setText("O'ldirish");
        killButton.setCallbackData("w_kom_kill_" + groupId + "_" + gameId + "_" + userId);

        rows.add(Collections.singletonList(checkButton));
        rows.add(Collections.singletonList(killButton));

        // "Otkazib yuborish" tugmasini qo‘shish
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.SHERIF.name() + ":" + groupId + ":" + userId)
                .build();

        rows.add(Collections.singletonList(skipButton));

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText("Nima qilamiz?");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendQoravul(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId) ||
                    qoravulLastTarget.getOrDefault(groupId, Collections.emptyMap()).containsKey(userId)) {
                continue;
            }
            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_qor_prtc_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);
            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.GUARD.name() + ":" + groupId + ":" + userId)
                .build();

        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText("Kimni himoya qilamiz?");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendDoc(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId) && (
                    doctorSelfUsed.getOrDefault(groupId, Collections.emptySet()).contains(userId)
                            || doctorLastTarget.getOrDefault(groupId, Collections.emptyMap()).containsKey(userId))) {
                continue;
            }
            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_doc_hlth_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);

            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.DOCTOR.name() + ":" + groupId + ":" + userId)
                .build();

        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText("💉 Kimni davolaymiz?");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendKezuvchi(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId) ||
                    kezLastTarget.getOrDefault(groupId, Collections.emptyMap()).containsKey(userId)) {
                continue;
            }
            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_kez_kez_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);

            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.SLEEPWALKER.name() + ":" + groupId + ":" + userId)
                .build();
        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText("Kimnikiga boramiz");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void docHealed(Long groupId, Long docId, Long targetId) {
        doctorLastTarget
                .computeIfAbsent(groupId, k -> new HashMap<>())
                .put(docId, targetId);
    }

    private void kezHealed(Long groupId, Long kezId, Long targetId) {
        kezLastTarget
                .computeIfAbsent(groupId, k -> new HashMap<>())
                .put(kezId, targetId);
    }

    private void qorHealed(Long groupId, Long qorId, Long targetId) {
        qoravulLastTarget
                .computeIfAbsent(groupId, k -> new HashMap<>())
                .put(qorId, targetId);
    }

    private void sendQotil(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId)) continue;

            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_qotil_kill_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);

            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }

        // "Otkazib yuborish" tugmasini qo‘shish
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.KILLER.name() + ":" + groupId + ":" + userId)
                .build();
        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText("Kimni o‘ldiramiz?");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendMafiaVoteMenu(Long voterId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        Map<Long, Role> roles = lobby.getPlayerRoles();

        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(voterId)) continue;

            Role otherRole = roles.get(otherPlayerId);

            // Mafia oilasi (Mafia, Don, Big Bro, Little Bro) ga ovoz berilmaydi
            if (otherRole == Role.MAFIA || otherRole == Role.DON
                    || otherRole == Role.BIG_BRO || otherRole == Role.LITTLE_BRO) {
                continue;
            }

            // Target o'yinchi nomini olish
            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            // Tugma yaratish
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(targetName)
                    .callbackData("w_mafia_kill_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + voterId)
                    .build();

            buttons.add(List.of(btn));
        }

        // ✅ "O‘tkazib yuborish" tugmasini oxiriga qo‘shamiz
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.MAFIA.name() + ":" + groupId + ":" + voterId)
                .build();
        buttons.add(List.of(skipButton));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        // Foydalanuvchi roli
        String roleName = roles.get(voterId).name();

        SendMessage message = new SendMessage();
        message.setChatId(voterId.toString());
        message.setText(getCleanFirstName(voterId, groupId) + " - " + roleName + "\n\n" +
                "Mafia keyingi qurboni uchun ovoz berish:");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);

        execute(message);
    }


    private void handleDonDeath(Long groupId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Map<Long, Role> playerRoles = lobby.getPlayerRoles();

        Long donId = null;
        for (Map.Entry<Long, Role> entry : playerRoles.entrySet()) {
            if (entry.getValue() == Role.DON) {
                donId = entry.getKey();
                break;
            }
        }

        if (donId == null || lobby.getAlivePlayers().contains(donId)) {
            return;
        }

        Long newDonId = null;
        for (Long playerId : lobby.getAlivePlayers()) {
            Role role = playerRoles.get(playerId);
            if (role == Role.MAFIA) {
                newDonId = playerId;
                break;
            }
        }

        if (newDonId != null) {
            playerRoles.put(newDonId, Role.DON);

            sendMessage(newDonId, "Tabriklaymiz! Siz endi Don bo'ldingiz. Mafiyalarga boshchilik qiling.");

            for (Map.Entry<Long, Role> entry : playerRoles.entrySet()) {
                Long id = entry.getKey();
                Role r = entry.getValue();
                if (!id.equals(newDonId) &&
                        (r == Role.MAFIA || r == Role.DON) &&
                        lobby.getAlivePlayers().contains(id)) {
                    sendMessage(id, getCleanFirstName(newDonId, groupId) + " endi yangi Don bo‘ldi.");
                }
            }
        }
    }

    private void sendDon(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        Map<Long, Role> roles = lobby.getPlayerRoles();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId)) continue;

            Role otherRole = roles.get(otherPlayerId);
            if (otherRole == Role.MAFIA || otherRole == Role.DON
                    || otherRole == Role.BIG_BRO || otherRole == Role.LITTLE_BRO) {
                continue;
            }

            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_don_kill_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);
            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.DON.name() + ":" + groupId + ":" + userId)
                .build();
        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText(getCleanFirstName(userId, groupId) + " - Don\n" +
                "Mafia keyingi qurboni uchun ovoz berish o'tkazyapti:");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendBIGBRO(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        Map<Long, Role> roles = lobby.getPlayerRoles();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId)) continue;

            Role otherRole = roles.get(otherPlayerId);
            if (otherRole == Role.MAFIA || otherRole == Role.DON
                    || otherRole == Role.BIG_BRO || otherRole == Role.LITTLE_BRO) {
                continue;
            }

            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_BIGBRO_kill_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);
            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.BIG_BRO.name() + ":" + groupId + ":" + userId)
                .build();
        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText(getCleanFirstName(userId, groupId) + " - Aka\n" +
                "Mafia keyingi qurboni uchun ovoz berish o'tkazyapti:");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendLITTLEBRO(Long userId, Long groupId, Long gameId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Set<Long> alivePlayers = lobby.getAlivePlayers();
        Map<Long, Role> roles = lobby.getPlayerRoles();

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (Long otherPlayerId : alivePlayers) {
            if (otherPlayerId.equals(userId)) continue;

            Role otherRole = roles.get(otherPlayerId);
            if (otherRole == Role.MAFIA || otherRole == Role.DON
                    || otherRole == Role.BIG_BRO || otherRole == Role.LITTLE_BRO) {
                continue;
            }

            GetChatMember targetMemberReq = new GetChatMember();
            targetMemberReq.setChatId(groupId);
            targetMemberReq.setUserId(otherPlayerId);
            ChatMember targetMember = execute(targetMemberReq);
            String targetName = targetMember.getUser().getFirstName();

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(targetName);
            btn.setCallbackData("w_LITTLEBRO_kill_" + groupId + "_" + gameId + "_" + otherPlayerId + "_" + userId);
            List<InlineKeyboardButton> rowBtn = new ArrayList<>();
            rowBtn.add(btn);
            buttons.add(rowBtn);
        }

        // "Otkazib yuborish" tugmasini qo‘shish
        InlineKeyboardButton skipButton = InlineKeyboardButton.builder()
                .text("O‘tkazib yuborish")
                .callbackData("skip_action:" + Role.LITTLE_BRO.name() + ":" + groupId + ":" + userId)
                .build();
        List<InlineKeyboardButton> skipRow = new ArrayList<>();
        skipRow.add(skipButton);
        buttons.add(skipRow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);

        SendMessage message = new SendMessage();
        message.setChatId(userId.toString());
        message.setText(getCleanFirstName(userId, groupId) + " - Uka\n" +
                "Mafia keyingi qurboni uchun ovoz berish o'tkazyapti:");
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        execute(message);
    }

    private void sendMafiaAction(long groupId, String otherPlayerId, Long playerId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        Map<Long, Role> roles = lobby.getPlayerRoles();
        GetChatMember targetMemberReq = new GetChatMember();
        targetMemberReq.setChatId(groupId);
        targetMemberReq.setUserId(Long.parseLong(otherPlayerId));
        ChatMember targetMember = execute(targetMemberReq);
        String targetName = targetMember.getUser().getFirstName();

        String message = getCleanFirstName(playerId, groupId) + " " + targetName + " ni tanladi!";

        for (Map.Entry<Long, Role> entry : roles.entrySet()) {
            Role role = entry.getValue();
            if (role == Role.MAFIA || role == Role.DON) {
                Long mafiaId = entry.getKey();
                sendMessage(mafiaId, message);
            }
        }
    }

    private void sendStartMessageWithButton(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🎮 O‘yin boshlandi!");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        InlineKeyboardButton viewRoleButton = new InlineKeyboardButton();
        viewRoleButton.setText("🕵️ Rolini ko‘rish");
        viewRoleButton.setCallbackData("VIEW_ROLE");

        keyboard.add(Collections.singletonList(viewRoleButton));
        markup.setKeyboard(keyboard);

        message.setReplyMarkup(markup);
        execute(message);
    }

    public void handleGameCommand(long groupId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(groupId);
        if (lobby != null) {
            execute(DeleteMessage.builder()
                    .chatId(groupId)
                    .messageId(lobby.getMessageId())
                    .build());
        } else {
            boolean created = lobbyManager.createLobby(groupId);
            if (!created) {
                sendMessage(groupId, "❗ O'yin yaratishda xatolik yuz berdi.");
                return;
            }
            lobby = lobbyManager.getLobby(groupId);
        }

        InlineKeyboardButton joinButton = InlineKeyboardButton.builder()
                .text("🚀 Qo‘shilish")
                .url("https://t.me/" + getBotUsername() + "?start=group_" + groupId)
                .build();
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(joinButton))
                .build();

        StringBuilder sb = new StringBuilder("Ro'yxatdan o'tish boshlandi!\n");

        List<String> playerList = new ArrayList<>();
        for (Long player : lobby.getPlayers()) {
            playerList.add(getUserMention(player, groupId));
        }

        SendMessage message = SendMessage.builder()
                .chatId(groupId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();

        Message sent = execute(message);
        lobby.setMessageId(sent.getMessageId());

        execute(PinChatMessage.builder()
                .chatId(groupId)
                .messageId(sent.getMessageId())
                .disableNotification(true)
                .build());
        System.out.println(groupId);

        timerManager.startTimerForGroup(groupId, "Ro'yxatdan o'tish", () -> {

            try {
                GameLobby currentLobby = lobbyManager.getLobby(groupId);
                if (currentLobby != null) {
                    execute(DeleteMessage.builder()
                            .chatId(groupId)
                            .messageId(currentLobby.getMessageId())
                            .build());

                    int count = currentLobby.getPlayers().size();
                    if (count < 4) {
                        sendMessage(groupId, "❌O‘yinni boshlash uchun kamida 4 ta ishtirokchi kerak.");
                        lobbyManager.removeLobby(groupId);
                    } else {
                        startGame(currentLobby, groupId);
                        currentLobby.startGame();
                    }
                }
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        });
    }
    private void rechargePlayer(Long playerId, GameLobby lobby, Long groupId) throws TelegramApiException {
        String name = getUserMention(playerId, groupId);
        String role = lobby.getPlayerRole(playerId);
        if (role == null) role = "noma'lum rol";
        lobby.killPlayer(playerId);
        String text = String.format("%s bu shahar o‘yinlaridan zerikdi va o‘zini osib qo‘ydi, u %s edi!", name, role);

        SendMessage msg = SendMessage.builder()
                .chatId(groupId.toString())
                .text(text)
                .parseMode("Markdown")
                .build();
        execute(msg);
        checkGameOver(groupId);
    }
    public void handleStartWithJoin(long userId, String[] args, String userName) throws TelegramApiException {
        if (args.length < 1 || !args[0].startsWith("group_")) {
            sendMessage(userId, "❗ Guruh ma'lumotlari topilmadi.");
            return;
        }

        long chatId;
        try {
            chatId = Long.parseLong(args[0].substring(6));
        } catch (NumberFormatException e) {
            sendMessage(userId, "❗ Guruh ID noto‘g‘ri.");
            return;
        }

        // 🔍 Foydalanuvchi boshqa lobbyda bo‘lsa, uni chiqaramiz
        GameLobby oldLobby = lobbyManager.getLobbyByPlayer(userId);
        if (oldLobby != null && oldLobby.getChatId() != chatId) {
            if (oldLobby.isGameStarted()) {
                // 🔥 Agar o‘yin boshlangan bo‘lsa — rechargePlayer chaqiramiz
                rechargePlayer(userId, oldLobby, oldLobby.getChatId());
                checkGameOver(chatId);
            } else {
                oldLobby.leavePlayer(userId);
            }
        }

        // 🔎 Hozirgi guruhdagi o‘yinni topamiz
        GameLobby lobby = lobbyManager.getLobby(chatId);
        if (lobby == null) {
            sendMessage(userId, "❗ Siz qo‘shilmoqchi bo‘lgan guruhda o‘yin mavjud emas.");
            return;
        }

        // 👥 Foydalanuvchini qo‘shamiz
        boolean added = lobby.addPlayer(userId, getCleanFirstName(userId, chatId));
        if (!added) {
            sendMessage(userId, "❗ Siz allaqachon bu o‘yinga qo‘shilgansiz.");
            return;
        }

        // 💬 Shaxsiy xabar
        SendMessage personalMessage = SendMessage.builder()
                .chatId(userId)
                .text("✅ Siz o‘yinga muvaffaqiyatli qo‘shildingiz.")
                .parseMode("Markdown")
                .replyMarkup(backToGroupButton(chatId))
                .build();
        execute(personalMessage);

        // 📝 Guruhdagi xabarni yangilaymiz
        StringBuilder sb = new StringBuilder("Ro‘yxatdan o‘tish davom etmoqda\n");
        sb.append("Ro‘yxatdan o‘tganlar:\n");

        List<String> playerList = new ArrayList<>();
        for (Long player : lobby.getPlayers()) {
            playerList.add(getUserMention(player, chatId));
        }

        sb.append(String.join(", ", playerList)).append("\n\n");
        sb.append("Jami: ").append(lobby.getPlayers().size()).append(" ta");

        InlineKeyboardButton joinButton = InlineKeyboardButton.builder()
                .text("🚀 Qo‘shilish")
                .url("https://t.me/" + getBotUsername() + "?start=group_" + chatId)
                .build();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(joinButton))
                .build();

        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(lobby.getMessageId())
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();
        execute(edit);
    }




    public void sendCurrentLobbyStatus(long chatId) throws TelegramApiException {
        GameLobby lobby = lobbyManager.getLobby(chatId);
        if (lobby == null) {
            sendMessage(chatId, "❗ Hozircha hech qanday o'yin boshlanmagan.");
            return;
        }
        StringBuilder sb = new StringBuilder("Ro'yxatdan o'tish davom etmoqda\n");
        sb.append("Ro'yxatdan o'tganlar:\n");

        List<String> playerList = new ArrayList<>();
        for (Long player : lobby.getPlayers()) {
            playerList.add(getUserMention(player, chatId));
        }

        sb.append(String.join(", ", playerList)).append("\n\n");
        sb.append("Jami: ").append(lobby.getPlayers().size()).append(" ta odam");

        InlineKeyboardButton joinButton = InlineKeyboardButton.builder()
                .text("🚀 Qo‘shilish")
                .url("https://t.me/" + getBotUsername() + "?start=group_" + chatId)
                .build();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(joinButton))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(markup)
                .build();

        Message sent = execute(message);
        PinChatMessage pin = new PinChatMessage();
        pin.setChatId(chatId);
        pin.setMessageId(sent.getMessageId());
        pin.setDisableNotification(true);
        execute(pin);
        lobby.setMessageId(sent.getMessageId());
    }

    public void sendShopMenu(long chatId) throws TelegramApiException {
        StringBuilder sb = new StringBuilder("🛍 Nima sotib olamiz?\n\n");

        sb.append("📁 Hujjatlar\n");
        sb.append("Kimdir sizning rolingizni tekshirmoqchi bo‘lsa, soxta hujjatlar yordam berishi mumkin\n\n");

        sb.append("🛡 Himoya\n");
        sb.append("Bir marta hayotingizni saqlab qoladi\n\n");

        sb.append("🧾 Rol\n");
        sb.append("Oldindan rol sotib olishingiz mumkin va keyingi o‘yinda ushbu rol bilan o‘ynashingiz mumkin.\n");

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(sb.toString())
                .parseMode("Markdown")
                .replyMarkup(createItemMenuFromDb())
                .build();

        execute(message);
    }

    public InlineKeyboardMarkup createItemMenuFromDb() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Tartiblangan o‘zbekcha nomlar
        Map<String, String> uzbekNames = new LinkedHashMap<>();
        uzbekNames.put("reset", "🔄 Statistika tiklash");
        uzbekNames.put("fakedoc", "📁 Hujjat");
        uzbekNames.put("shield", "🛡 Himoya");
        uzbekNames.put("mask", "🎭 Maska");
        uzbekNames.put("gun", "🔫 Miltiq");
        uzbekNames.put("killershield", "⛑️ Qotildan himoya");
        uzbekNames.put("voteshield", "⚖️ Ovoz himoya");

        String query = "SELECT name, price, currency FROM items";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {

            // Natijalarni map tartibida chiqarish uchun oldin DB itemlarini yig‘amiz
            Map<String, String[]> dbItems = new HashMap<>();

            while (rs.next()) {
                String name = rs.getString("name");
                int price = rs.getInt("price");
                String currency = rs.getString("currency");

                dbItems.put(name, new String[]{String.valueOf(price), currency});
            }

            // Endi mapdagi tartib bo‘yicha tugmalarni joylashtiramiz
            for (Map.Entry<String, String> entry : uzbekNames.entrySet()) {
                String key = entry.getKey();
                String displayName = entry.getValue();

                if (dbItems.containsKey(key)) {
                    String[] values = dbItems.get(key);
                    String price = values[0];
                    String currency = values[1];

                    InlineKeyboardButton button = InlineKeyboardButton.builder()
                            .text(displayName + " - " + price + " " + currency)
                            .callbackData("by_" + key + "_" + price + "_" + currency)
                            .build();

                    rows.add(List.of(button));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Qo‘shimcha tugmalar
        rows.add(List.of(InlineKeyboardButton.builder().text("🧾 Faol rol").callbackData("buy_role").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text("⬅️ Orqaga").callbackData("back_main_menu").build()));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private void showPremiumGroups(Long chatId, int page, Integer messageId) {
        List<PremiumGroup> groups = PremiumGroupManager.loadGroups().stream()
                .sorted(Comparator.comparingInt(PremiumGroup::getAmount).reversed()) // kamayish tartibida
                .limit(15) // faqat 15 ta
                .toList();

        if (groups.isEmpty()) {
            try {
                execute(EditMessageText.builder()
                        .chatId(chatId.toString())
                        .messageId(messageId)
                        .text("⚠️ Hozircha premium guruhlar mavjud emas.")
                        .build());
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) groups.size() / pageSize);

        int start = page * pageSize;
        int end = Math.min(start + pageSize, groups.size());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = start; i < end; i++) {
            PremiumGroup group = groups.get(i);

            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText(group.getGroupName() + " 💎 " + group.getAmount() + " olmos");
            btn.setUrl(group.getInviteLink());

            rows.add(List.of(btn));
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();

        if (page > 0) {
            InlineKeyboardButton prevBtn = new InlineKeyboardButton();
            prevBtn.setText("⬅️ Orqaga");
            prevBtn.setCallbackData("premium_prev_" + (page - 1));
            navRow.add(prevBtn);
        }

        if (page < totalPages - 1) {
            InlineKeyboardButton nextBtn = new InlineKeyboardButton();
            nextBtn.setText("➡️ Oldinga");
            nextBtn.setCallbackData("premium_next_" + (page + 1));
            navRow.add(nextBtn);
        }

        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("🔙 Ortga");
        backBtn.setCallbackData("main_menu");
        rows.add(List.of(backBtn));

        markup.setKeyboard(rows);

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .text("🔥 Premium guruhlar ro‘yxati (" + (page + 1) + "/" + totalPages + "):")
                .replyMarkup(markup)
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean isCurrentGameCallback(String callbackData) {
        System.out.println(callbackData);
        String[] parts = callbackData.split("_");
        Long groupId = Long.valueOf(parts[3]);

        GameLobby lobby = lobbyManager.getLobby(groupId);
        if (lobby == null) return false;
        try {
            long gameIdFromCallback = Long.parseLong(parts[4]);
            return gameIdFromCallback == lobby.getGameId();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isOwner(Long userId) {
        String query = "SELECT 1 FROM admins WHERE user_id = ? LIMIT 1";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query)) {

            statement.setLong(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next(); // agar chiqsa -> admin
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void sendOwnerMenu(Long chatId) {
        System.out.println(chatId);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("🎮 O'yin boshlash");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("👥 O'yinchilar ro'yxati");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("♻️ Rollarni qayta taqsimlash");

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Admin Paneli");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendBuyOptionsWithPrices(Long chatId) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String query = "SELECT count_diamonds, price, currency, description FROM prices ORDER BY count_diamonds ASC";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {

            List<InlineKeyboardButton> allButtons = new ArrayList<>();

            while (rs.next()) {
                int countDiamonds = rs.getInt("count_diamonds");
                String price = rs.getString("price");
                String currency = rs.getString("currency");
                String desc = rs.getString("description");

                String buttonText = "💎 " + countDiamonds + " - " + price + " " + currency;
                if (desc != null && !desc.isBlank()) {
                    buttonText += " " + desc;
                }

                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(buttonText);
                button.setCallbackData("buyDM_" + countDiamonds);

                allButtons.add(button);
            }

            // 🔹 Oxirgi 2 tugmani ajratib olamiz
            int size = allButtons.size();
            List<InlineKeyboardButton> normalButtons = allButtons.subList(0, Math.max(0, size - 2));
            List<InlineKeyboardButton> lastTwo = allButtons.subList(Math.max(0, size - 2), size);

            // 🔹 Normal tugmalarni 2 tadan chiqarish
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int i = 0; i < normalButtons.size(); i++) {
                row.add(normalButtons.get(i));
                if ((i + 1) % 2 == 0) {
                    rows.add(row);
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }

            // 🔹 Oxirgi 2 tugma → alohida qatorlarda
            for (InlineKeyboardButton btn : lastTwo) {
                rows.add(List.of(btn));
            }

            // 🔹 Orqaga tugmasi
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("◀️ orqaga");
            backButton.setCallbackData("back_main_menu");
            rows.add(List.of(backButton));

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(rows);

            sendMessage.setReplyMarkup(markup);
            sendMessage.setText("Xarid qiling 💎");
            sendMessage.setChatId(chatId);

        } catch (SQLException e) {
            e.printStackTrace();
            sendMessage.setText("❌ Narxlar yuklanmadi.");
            sendMessage.setChatId(chatId);
        }

        execute(sendMessage);
    }

    public void sendPaymentInstruction(Long chatId, int amount, int day, String currency) throws TelegramApiException {
        isWaitingForPaymentMap.put(chatId, true);
        paymentAmountMap.put(chatId, day);
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        String text = "\uD83D\uDCB3 <b>" + amount + " so'm</b> ni to‘lash uchun quyidagi karta raqamiga to‘lovni amalga oshiring:\n\n" +
                "\uD83D\uDCB5 <code>9860 3501 4855 1277</code>\n\n" +
                "\uD83D\uDD39 To‘lovni amalga oshirib, <b>check (skrinshot)</b>ni yuboring.\n\n" +
                "\uD83D\uDCC5 Xizmat muddati to‘lov tasdiqlangach faollashadi.\n" +
                "Bekor qilish uchun /cancel buyrug'ini yuboring!";
        message.setText(text);
        message.setParseMode("HTML");
        Message sentMessage = execute(message);
        paymentMessageIdMap.put(chatId, sentMessage.getMessageId());
    }

    public void sendTariffOptionsReal(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📦 Tariflardan birini tanlang:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String sql = "SELECT days, price, currency FROM tariffs ORDER BY days";

        try (Connection conn = DriverManager.getConnection(
                DB_URL, USER, PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String days = String.valueOf(rs.getInt("days"));
                String price = String.valueOf(rs.getInt("price"));
                String currency = rs.getString("currency");

                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(days + " kun - " + price + " " + currency);
                button.setCallbackData("TARIFF_" + days + "_" + price + "_" + currency);

                rows.add(List.of(button));
            }

        } catch (Exception e) {
            e.printStackTrace();
            message.setText("❌ Tariflarni o‘qishda xatolik yuz berdi.");
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ orqaga");
        backButton.setCallbackData("back_to_pursache");
        rows.add(List.of(backButton));

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        execute(message);
    }

    private void deleteMessage(long chatId, int messageId) throws TelegramApiException {
        DeleteMessage delete = new DeleteMessage(String.valueOf(chatId), messageId);
        execute(delete);
    }

    private void handleBuyItem(CallbackQuery callbackQuery, long chatId, int cost, String itemName, String type, String successMsg) throws TelegramApiException {
        boolean hasEnough = type.equals("diamond") ? getDiamondCount(chatId) >= cost : getDollarByChatId(chatId) >= cost;

        if (hasEnough) {
            decreaseCurrencyAndAddItem(chatId, cost, itemName, type);
            showModal(callbackQuery, successMsg);
        } else {
            showModal(callbackQuery, "Xisobingizda " + (type.equals("diamond") ? "olmos" : "pul") + " yetarli emas!");
        }
    }

    public void showModal(CallbackQuery callbackQuery, String text) throws TelegramApiException {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(text)
                .showAlert(true)
                .build();

        execute(answer);
    }

    private void sendMessage(Long chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        execute(message);
    }

    public void sendPurchaseOptions(Long chatId) throws TelegramApiException {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        String query = "SELECT dollar, diamond FROM purchase_options";

        try (Connection connection = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement statement = connection.prepareStatement(query);
             ResultSet rs = statement.executeQuery()) {

            List<InlineKeyboardButton> row = new ArrayList<>();

            while (rs.next()) {
                int dollar = rs.getInt("dollar");
                int diamond = rs.getInt("diamond");

                String text = "💸 " + dollar + " - 💎 " + diamond;
                String callbackData = "buy_dollar_" + dollar + "_" + diamond;

                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(text)
                        .callbackData(callbackData)
                        .build();

                row.add(button);

                if (row.size() == 2) {
                    keyboard.add(row);
                    row = new ArrayList<>();
                }
            }

            if (!row.isEmpty()) {
                keyboard.add(row);
            }

            keyboard.add(List.of(
                    InlineKeyboardButton.builder()
                            .text("⬅️ orqaga")
                            .callbackData("back_main_menu")
                            .build()
            ));

        } catch (SQLException e) {
            e.printStackTrace();
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Narxlarni yuklashda xatolik yuz berdi.")
                    .build());
            return;
        }

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId.toString())
                .text("Xarid qilish 💸")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(keyboard).build())
                .build();

        execute(sendMessage);
    }

    private void sendUserStatsWithButtons(long chatId, String resultMessage) throws TelegramApiException {
        UserStats stats = getUserStats(chatId);

        StringBuilder sb = new StringBuilder();

        if (resultMessage != null && !resultMessage.isEmpty()) {
            sb.append(resultMessage).append("\n\n");
        }

        if (stats.is_Pro()) {
            sb.append("⭐️ ");
        }

        sb.append("ID: ").append(stats.getChatId()).append("\n\n");
        sb.append("👤 ").append(stats.getFirstName()).append("\n\n");
        sb.append("💵 Dollar: ").append(stats.getDollar()).append("\n");
        sb.append("💎 Olmos: ").append(stats.getDiamond()).append("\n\n");

        sb.append("🛡 Himoya: ").append(stats.getShield()).append("\n");
        sb.append("⛑️ Qotildan himoya: ").append(stats.getMurderProtect()).append("\n");
        sb.append("⚖️ Ovoz berishni himoya qilish: ").append(stats.getVoteProtect()).append("\n");
        sb.append("🔫 Miltiq: ").append(stats.getGun()).append("\n\n");
        sb.append("🎭 Maska: ").append(stats.getMask()).append("\n");
        sb.append("📁 Soxta hujjat: ").append(stats.getFakeDoc()).append("\n");
        sb.append("🃏 Keyingi o'yindagi rolingiz: ").append(stats.getRole()).append("\n\n");

        sb.append("🎯 G'alabalar: ").append(stats.getWin()).append("\n");
        sb.append("🎲 Jami o'yinlar: ").append(stats.getPlayCount());

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(sb.toString());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("🔫 - " + onOff(stats.isGunActive()))
                        .callbackData("toggle_gun").build(),
                InlineKeyboardButton.builder()
                        .text("🛡 - " + onOff(stats.isShieldActive()))
                        .callbackData("toggle_shield").build(),
                InlineKeyboardButton.builder()
                        .text("🎭 - " + onOff(stats.isMaskActive()))
                        .callbackData("toggle_mask").build()
        ));
        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("⛑️ - " + onOff(stats.isMurderProtectActive()))
                        .callbackData("toggle_killershield").build(),
                InlineKeyboardButton.builder()
                        .text("⚖️ - " + onOff(stats.isVoteProtectActive()))
                        .callbackData("toggle_voteshield").build(),
                InlineKeyboardButton.builder()
                        .text("📁 - " + onOff(stats.isFakeDocActive()))
                        .callbackData("toggle_fakedoc").build()
        ));
        rows.add(List.of(
                InlineKeyboardButton.builder().text("🛒 Do‘kon").callbackData("open_shop").build(),
                InlineKeyboardButton.builder().text("⭐️ Pro sotib olish").callbackData("buy_pro").build()
        ));
        rows.add(List.of(
                InlineKeyboardButton.builder().text("💵 Xarid qilish").callbackData("buy_dlr").build(),
                InlineKeyboardButton.builder().text("💎 Xarid qilish").callbackData("buy_dmnd").build()
        ));
        rows.add(List.of(
                InlineKeyboardButton.builder().text("🎲 Premium guruhlar").callbackData("premium_groups").build()
        ));
        rows.add(List.of(
                InlineKeyboardButton.builder()
                        .text("🆕 Yangiliklar")
                        .url("https://t.me/CamelotMafiaNews") // guruh yoki kanal linki
                        .build()
        ));

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(rows).build();
        message.setReplyMarkup(markup);
        execute(message);
    }

    private String onOff(boolean active) {
        return active ? "\uD83D\uDFE2ON" : "\uD83D\uDD34OFF";
    }

    private InlineKeyboardMarkup getBotButton() {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("🤖 Botga o‘tish");
        button.setUrl("https://t.me/" + getBotUsername()); // <-- o‘zingizning bot username

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);

        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);

        return markup;
    }

    public void sendPhotoToAdminForApproval(Message message) throws TelegramApiException {
        Long userId = message.getChatId();
        String firstName = message.getFrom().getFirstName();
        int day = paymentAmountMap.get(userId);
        int messageId = paymentMessageIdMap.get(userId);
        deleteMessage(userId, messageId);
        List<PhotoSize> photoList = message.getPhoto();
        String fileId = photoList.get(photoList.size() - 1).getFileId();

        String userLink = "<a href=\"tg://user?id=" + userId + "\">" + firstName + "</a>";
        String caption = "💸 " + userLink + day + " kunlik Pro versiya uchun check yubordi!";

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(Long.parseLong(admin_id));
        sendPhoto.setPhoto(new InputFile(fileId));
        sendPhoto.setCaption(caption);
        sendPhoto.setParseMode("HTML");

        InlineKeyboardButton confirmButton = new InlineKeyboardButton("✅ Tasdiqlash");
        confirmButton.setCallbackData("PAY_CONFIRM_" + userId);

        InlineKeyboardButton cancelButton = new InlineKeyboardButton("❌ Bekor qilish");
        cancelButton.setCallbackData("PAY_REJECT_" + userId);

        List<InlineKeyboardButton> row = List.of(confirmButton, cancelButton);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(row));
        sendPhoto.setReplyMarkup(markup);

        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    @Override
    public String getBotToken() {
        return "8452121817:AAHxLYmTFul11HaHm43hJVdfE9TlUPf8LE4";
    }


//
//    @Override
//    public String getBotToken() {
//        return "7943940440:AAFhofF96RYFMcTiWJg3Rs4p2q6LzLQvY14";
//    }


    @Override
    public String getBotUsername() {
        return "CamelotMafiaBot";
    }


//    @Override
//    public String getBotUsername() {
//        return "true_maf_java_bot";
//    }

    public void sendAskSex(Long chatId) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText("Jinsingizni tanlang:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton maleButton = new InlineKeyboardButton();
        maleButton.setText("🧔 Erkak");
        maleButton.setCallbackData("gender_male");

        InlineKeyboardButton femaleButton = new InlineKeyboardButton();
        femaleButton.setText("👩 Ayol");
        femaleButton.setCallbackData("gender_female");

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(maleButton);
        row.add(femaleButton);

        rows.add(row);
        markup.setKeyboard(rows);
        sendMessage.setReplyMarkup(markup);

        execute(sendMessage);
    }

    public void sendHelloMenu(Long chatId, String userName) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setParseMode("HTML");
        String text = "👋 Assalamu alaykum " + userName + " \n\n"
                + "🏰 <b>Camelot Mafia Bot</b>-ga xush kelibsiz!\n\n"
                + "Bu shunchaki o'yin emas - bu sizning\n"
                + "Telegram’dagi <b><i>sirli jamoangiz</i></b>.\n\n"
                + "👥 Guruhga qo‘shiling, qoidalarni\n"
                + "o‘qing va o‘yindan zavqlaning!\n\n"
                + "🎭 <i>Sizni rolingiz kutmoqda...</i>\n\n";

        sendMessage.setText(text);
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        InlineKeyboardButton joinGroupButton = new InlineKeyboardButton();
        joinGroupButton.setText("➕ Guruhga qo‘shish");
        joinGroupButton.setUrl("https://t.me/CamelotMafiaBot?startgroup=add");
        keyboard.add(List.of(joinGroupButton));
        InlineKeyboardButton rulesButton = new InlineKeyboardButton();
        rulesButton.setText("📜 Qoidalar");
        rulesButton.setUrl("https://t.me/mafqoida");
        keyboard.add(List.of(rulesButton));
        InlineKeyboardButton premiumGroupsButton = new InlineKeyboardButton();
        premiumGroupsButton.setText("💎 Premium Guruhlar");
        premiumGroupsButton.setCallbackData("premium_groups");
        keyboard.add(List.of(premiumGroupsButton));
        InlineKeyboardButton helpButton = new InlineKeyboardButton();
        helpButton.setText("❓ Yordam");
        helpButton.setUrl("https://t.me/bttasi");
        InlineKeyboardButton updatesButton = new InlineKeyboardButton();
        updatesButton.setText("📰 Yangiliklar");
        updatesButton.setUrl("https://t.me/CamelotMafiaNews");
        keyboard.add(List.of(helpButton, updatesButton));
        inlineKeyboard.setKeyboard(keyboard);
        sendMessage.setReplyMarkup(inlineKeyboard);
        execute(sendMessage);
    }

    public void sendProOptions(Long chatId) {
        String messageText = "📌 Endi PRO hisobni 3 xil yo‘l bilan sotib olish mumkin:";

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(messageText);
        sendMessage.setParseMode("HTML");

        InlineKeyboardButton realButton = new InlineKeyboardButton("💵 Real pul orqali");
        realButton.setCallbackData("pro_real");

        InlineKeyboardButton starButton = new InlineKeyboardButton("⭐ Stars orqali");
        starButton.setCallbackData("pro_star");

        InlineKeyboardButton almazButton = new InlineKeyboardButton("💎 Almaz orqali");
        almazButton.setCallbackData("pro_almaz");

        InlineKeyboardButton backButton = new InlineKeyboardButton("⬅️ Orqaga");
        backButton.setCallbackData("back_main_menu");

        List<InlineKeyboardButton> row1 = List.of(realButton, starButton, almazButton);
        List<InlineKeyboardButton> row2 = List.of(backButton);

        List<List<InlineKeyboardButton>> keyboard = List.of(row1, row2);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);

        sendMessage.setReplyMarkup(markup);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void sendTariffOptions(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Tarifdan birini tanlang:");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        InlineKeyboardButton tariffBtn = new InlineKeyboardButton();
        tariffBtn.setText("30 kun — 45 ta 💎");
        tariffBtn.setCallbackData("tariff_30_45");

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("⬅️ Orqaga");
        backBtn.setCallbackData("back_to_pursache");

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(tariffBtn);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(backBtn);

        rowsInline.add(row1);
        rowsInline.add(row2);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public InlineKeyboardMarkup backToGroupButton(Long groupId) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText("🔙 Guruhga qaytish");
        button.setUrl("https://t.me/c/" + groupId.toString().substring(4));
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        keyboard.add(row);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }
}