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
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ChatBotService {

    private static final Logger logger = LogManager.getLogger(ChatBotService.class);

    private static final String endPoint = "http://msg.beowulfchain.com:8080/accounts/create";

    private static final String host = "beowulfchain.com";

    private static final String apiKey = "d342dbae70ed8ea3b68c2f007a22e6e0";

    private static final String defaultPwd = "123456789";

    private static final int MAX_T = 5;

    private static int difficult = 4;

    private static Admin admin;

    private static List<Bot> bots;

    public ChatBotService() {
    }

    private void init() throws XmppStringprepException {
        admin = new Admin("25251325", "123456789", "Admin", null);
        admin.start();

        bots = new ArrayList<>();
        generateBots(10);
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
                logger.info(response + ": " + nickName);
                bots.add(new Bot(request.getChatId(), request.getPassword(), nickName, null));
            }
        } catch (Exception e) {
            logger.error("Error create account");
            e.printStackTrace();
        }
    }

    private void generateBots(int numberOfMembers) {
        while (bots.size() < numberOfMembers) {
            registerChatAccount(String.format("bot%d", bots.size()));
            bots.get(bots.size() - 1).run();
        }
    }

    public void createNewConference(int numberOfMembers, String roomId, String password, boolean needTrack) {

        generateBots(numberOfMembers);

        RoomProperties newRoom = new RoomProperties(
                roomId,
                password,
                admin.getNickname()
        );

        MultiUserChat currentRoom = admin.createRoom(newRoom, needTrack);

        if (currentRoom == null) {
            return;
        }

        ExecutorService botPool = Executors.newFixedThreadPool(MAX_T);

        for (int i = 0; i < numberOfMembers; i++) {
            int finalI = i;
            Runnable joinRoomTask = () -> bots.get(finalI).joinRoom(newRoom);
            botPool.execute(joinRoomTask);
        }
        System.out.println("Created room with ID: " + roomId + "successfully");
    }

    public void inviteToNewConference(int numberOfMembers, String roomId, String password, boolean needTrack) {

        generateBots(numberOfMembers);

        RoomProperties newRoom = new RoomProperties(
                roomId,
                password,
                admin.getNickname()
        );

        MultiUserChat currentRoom = admin.createRoom(newRoom, needTrack);

        if (currentRoom == null) {
            return;
        }

        ExecutorService botPool = Executors.newFixedThreadPool(MAX_T);

        for (int i = 0; i < numberOfMembers; i++) {
            int finalI = i;
            Runnable inviteTask = () -> admin.sendInvitation(currentRoom, bots.get(finalI).userIdToJid(), "join");
            botPool.execute(inviteTask);
        }

        System.out.println("Created room with ID: " + roomId + "successfully");
    }

    private static void onDestroy() {
        try {
            admin.waitForExit();

            for (Bot bot : bots) {
                bot.waitForExit();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws XmppStringprepException {
        ChatBotService service = new ChatBotService();
        service.init();

        System.out.println("\n");
        System.out.println("- [-create <passcode> <number of members>] - create new room with your passcode");
        System.out.println("- [-invite <passcode> <number of members>] - invite list user to new room with your passcode");
        System.out.println("- [-rooms] - list rooms");
        System.out.println("- [exit] - exit");

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("\nEnter command to continue:");
            String input = in.nextLine();
            if (input == null || "".equals(input))
                continue;
            if ("exit".equals(input)) {
                onDestroy();
                return;
            }
            if ("\n".equals(input))
                continue;

            String[] params = input.split(" ");
            if ("-rooms".equals(params[0])) {
                for (RoomProperties room : admin.getCreatedRooms()) {
                    System.out.println("- " + room.getRoomId() + ": " + admin.getMembersCount(room.getRoomId()));
                }
            } else if ("-create".equals(params[0])) {
                String roomId = String.valueOf(System.currentTimeMillis());
                String passcode = (params.length >= 2) ? params[1] : "1234";
                int numberOfMembers = (params.length >= 3) ? Integer.parseInt(params[2]) : 100;
                service.createNewConference(numberOfMembers, roomId, passcode, true);
            } else if ("-invite".equals(params[0])) {
                String roomId = String.valueOf(System.currentTimeMillis());
                String passcode = (params.length >= 2) ? params[1] : "1234";
                int numberOfMembers = (params.length >= 3) ? Integer.parseInt(params[2]) : 100;
                service.inviteToNewConference(numberOfMembers, roomId, passcode, true);
            }
        }
    }
}
