package com.smack.example;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jxmpp.jid.impl.JidCreate;

import java.util.LinkedHashMap;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

import static com.smack.example.ConsoleColors.*;

public class App {

    private static SmackClient smackClient;
    private static LinkedHashMap<String, MultiUserChat> multiUserChatMap = new LinkedHashMap<>();
    private static LinkedHashMap<String, LinkedBlockingQueue<Message>> pendingMessage = new LinkedHashMap<>();
    private static String currentRoom;
    private static MultiUserChat currentChat;

    private static LinkedHashMap<String, String> userColorMap = new LinkedHashMap<>();
    private static Queue<String> COLOR_INDEX;

    public static void sout(String text, String color) {
        System.out.println(color + text + ConsoleColors.RESET);
    }

    private static void printMessage(Message message) {
        String from = message.getFrom().toString();
        String color = userColorMap.get(from);
        if (StringUtils.isEmpty(color)) {
            color = COLOR_INDEX.poll();
            COLOR_INDEX.add(color);
            userColorMap.put(from, color);
        }
        sout(from + ": " + message.getBody(), color);
    }

    public static void main(String[] args) throws Exception {
        COLOR_INDEX = new LinkedBlockingQueue<String>() {{
            put(RED);
            put(GREEN);
            put(YELLOW);
            put(BLUE);
            put(PURPLE);
            put(CYAN);
            put(WHITE);
        }};

        String username = System.getProperty("user");
        String password = System.getProperty("pwd");
        String nickName = System.getProperty("name");
        smackClient = new SmackClient(username, password);
        if (!smackClient.isAuthenticated()) {
            System.out.println("Connect failed");
            return;
        }

        System.out.println("\n");
        System.out.println("- [-create <passcode>] - create new room with your passcode");
        System.out.println("- [-join <room-id> <room passcode>] - join room with passcode");
        System.out.println("- [-rooms] - list rooms");
        System.out.println("- [-sc <room-id>] - switch chat to room-id");
        System.out.println("- [exit] - exit");

        MessageListener messageListener = (message) -> {
            String roomId = message.getFrom().getLocalpartOrNull().toString();
            if (!roomId.equals(currentRoom)) {
                LinkedBlockingQueue<Message> pendingMessages = pendingMessage.computeIfAbsent(roomId, k -> new LinkedBlockingQueue<>());
                pendingMessages.add(message);
            } else {
                printMessage(message);
            }
        };

        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.println("\nEnter command to continue:");
            String input = in.nextLine();
            if (input == null || "".equals(input))
                continue;
            if ("exit".equals(input))
                return;
            if ("\n".equals(input))
                continue;

            String[] params = input.split(" ");
            if ("-rooms".equals(params[0])) {
                for (String roomId : multiUserChatMap.keySet()) {
                    System.out.println("- " + roomId);
                }
                continue;
            } else if ("-create".equals(params[0])) {
                String roomId = String.valueOf(System.currentTimeMillis());
                String passcode = params[1];
                MultiUserChat multiUserChat = smackClient.createRoom(roomId, nickName, passcode, nickName + "/Demo group");
                System.out.println("Created room with ID: " + roomId);
                multiUserChat.addMessageListener(messageListener);
                multiUserChat.sendMessage("Hello room");
                multiUserChatMap.put(roomId, multiUserChat);
                currentChat = multiUserChat;
                currentRoom = currentChat.getRoom().getLocalpart().toString();
                startChatting(in);
                leave();
            } else if ("-join".equals(params[0])) {
                String roomId = params[1];
                MultiUserChat multiUserChat = multiUserChatMap.get(roomId);
                if (multiUserChat == null) {
                    String passcode = params.length >= 3 ? params[2] : null;
                    multiUserChat = smackClient.joinRoom(roomId, passcode, nickName);
                    if (multiUserChat == null) {
                        System.out.println("Join failed");
                        continue;
                    }
                    multiUserChatMap.put(roomId, multiUserChat);
                    multiUserChat.addMessageListener(messageListener);
                    multiUserChat.sendMessage("Hello room");
                }
                currentChat = multiUserChat;
                currentRoom = currentChat.getRoom().getLocalpart().toString();
                startChatting(in);
                leave();
            } else if ("-sc".equals(params[0])) {
                String roomId = params[1];
                MultiUserChat multiUserChat = multiUserChatMap.get(roomId);
                if (multiUserChat == null) {
                    System.out.println("Room not found");
                    continue;
                }
                currentChat = multiUserChat;
                currentRoom = currentChat.getRoom().getLocalpart().toString();
                startChatting(in);
                leave();
            }
        }
    }

    private static void startChatting(Scanner in) {
        System.out.println("\nCurrent room: " + currentRoom);
        System.out.println("Type your message to send (type [exit] to leave room):");
        printPendingMessage();
        while (true) {
            String input = in.nextLine();
            if (input == null || "".equals(input))
                continue;
            if ("exit".equals(input))
                return;
            if ("\n".equals(input))
                continue;
            if (input.startsWith("-invite ")) {
                String[] params = input.split(" ");
                for (String chatId : params) {
                    if ("-invite".equals(chatId))
                        continue;
                    try {
                        String jid = chatId + "@" + SmackClient.service;
                        currentChat.invite(JidCreate.entityBareFrom(jid), "join");
                        System.out.println("Sent invite to: " + jid);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return;
            } else if (input.startsWith("-sc ")) {
                String[] params = input.split(" ");
                String roomId = params[1];
                MultiUserChat multiUserChat = multiUserChatMap.get(roomId);
                if (multiUserChat == null) {
                    System.out.println("Room not found");
                    continue;
                }
                currentChat = multiUserChat;
                currentRoom = currentChat.getRoom().getLocalpart().toString();
                System.out.println("\nCurrent room: " + currentRoom);
                System.out.println("Type your message to send (type [exit] to leave room):");
                printPendingMessage();
                continue;
            }
            try {
                currentChat.sendMessage(input);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void leave() throws Exception {
        if (currentChat != null) {
            currentChat = null;
            currentRoom = "";
        }
    }

    private static void printPendingMessage() {
        if (!StringUtils.isEmpty(currentRoom)) {
            LinkedBlockingQueue<Message> pendingMessages = pendingMessage.computeIfAbsent(currentRoom, k -> new LinkedBlockingQueue<>());
            Message message = null;
            while ((message = pendingMessages.poll()) != null) {
                printMessage(message);
            }
        }
    }
}
