package com.smack.example;

import com.smack.example.communicate.GsonSingleton;
import com.smack.example.communicate.HttpUtility;
import com.smack.example.model.Admin;
import com.smack.example.model.Bot;
import com.smack.example.model.RegisterChatAccountRequest;
import com.smack.example.model.RoomProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.ArrayList;
import java.util.List;

import static com.smack.example.model.Admin.userIdToJid;


public class ChatBotService {

    private static final Logger logger = LogManager.getLogger(ChatBotService.class);

    private static final String endPoint = "http://msg.beowulfchain.com:8080/accounts/create";

    private static final String host = "beowulfchain.com";

    private static final String apiKey = "d342dbae70ed8ea3b68c2f007a22e6e0";

    private static final String defaultPwd = "123456789";

    private static int difficult = 4;

    private Admin admin;

    private MultiUserChat currentRoom;

    private List<Bot> bots;

    public ChatBotService() {
    }

    private void init() throws XmppStringprepException {
        admin = new Admin("25251325", "123456789", "Admin", null);
        admin.start();
        bots = new ArrayList<>();
    }

    private void registerChatAccount(String nickName) {
        try {
            String userId = RandomUtils.randomString(difficult);

            RegisterChatAccountRequest request = new RegisterChatAccountRequest(
                    apiKey,
                    host,
                    userId,
                    defaultPwd);

            String response = HttpUtility.sendPost(endPoint, GsonSingleton.getInstance().toJson(request));
            if (response == null || response.equals("")) {
                return;
            }

            if (response.contains("successfully")) {
                logger.info(response);
                bots.add(new Bot(request.getChatId(), request.getPassword(), nickName, null));
            }
        } catch (Exception e) {
            logger.error("Error create account");
            e.printStackTrace();
        }
    }

    public void createNewConference() {
        for (int i = 1; i <= 50; i++) {
            registerChatAccount(String.format("bot%d", i));
        }

        String roomId = String.valueOf(System.currentTimeMillis());
        String password = "1234";

        RoomProperties newRoom = new RoomProperties(
                roomId,
                password,
                admin.getNickname()
        );

        currentRoom = admin.createRoom(newRoom, true);
        List<String> jids = new ArrayList<>();

        for (Bot bot : bots) {
            try {
                bot.run();
                jids.add(userIdToJid(bot.getUsername()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        admin.inviteUsers(currentRoom, jids, "join");
    }

    public static void main(String[] args) throws XmppStringprepException {
        ChatBotService service = new ChatBotService();
        service.init();
        service.createNewConference();
    }
}
