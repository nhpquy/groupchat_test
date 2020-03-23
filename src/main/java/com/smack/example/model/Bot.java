package com.smack.example.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Bot implements Runnable, ConnectionListener {

    private static final Logger logger = LogManager.getLogger(Bot.class);

    public static String host = "msg.beowulfchain.com";
    public static int port = 443;
    public static String service = "beowulfchain.com";

    private final Lock lock;
    private final Condition running;
    private final XMPPTCPConnection connection;
    private ChatManager chatManager;
    private MultiUserChatManager multiUserChatManager;

    private String username;
    private String password;
    private String nickname;

    private List<RoomProperties> rooms;
    private ConcurrentHashMap<String, MultiUserChat> groupChatsByRoomId;

    private Timer timer;

    public Bot(String username, String password, String nickname, String roomsString) throws XmppStringprepException {

        lock = new ReentrantLock();
        running = lock.newCondition();

        this.username = username;
        this.password = password;
        this.nickname = nickname;

        this.connection = new XMPPTCPConnection(initSmackConfig());

        // Load joined rooms
        if (roomsString == null) {
            rooms = Collections.emptyList();
        } else {
            rooms = getCreateRooms(roomsString);
        }

        groupChatsByRoomId = new ConcurrentHashMap<>();
        timer = new Timer();
    }

    private XMPPTCPConnectionConfiguration initSmackConfig() throws XmppStringprepException {
        return XMPPTCPConnectionConfiguration.builder()
                .setHost(host)
                .setPort(port)
                .setUsernameAndPassword(username, password)
                .setXmppDomain(service)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                .setSendPresence(true)
                .addEnabledSaslMechanism(Arrays.asList("PLAIN", "X-OAUTH2", "SCRAM-SHA-1"))
                .build();
    }

    private List<RoomProperties> getCreateRooms(String roomsString) {
        return null;
    }

    @Override
    public void run() {
        logger.info("I'm " + username);
        start();
    }

    /**
     * Connect to the server
     */
    private void start() {
        try {
            connection.connect();

            if (connection.isConnected())
                logger.info("Smack Message Client connected to server: " + host + ":" + port);

            connection.login();

            if (connection.isAuthenticated())
                logger.info("Smack Message Client authenticated: username=" + username + "; password=XXXXXXXXXXXX");

            chatManager = ChatManager.getInstanceFor(connection);
            chatManager.addIncomingListener((EntityBareJid from, Message message, Chat chat) -> {
                logger.info("Received private message: " + message.getBody());
            });

            multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);

            rooms.forEach(this::joinRoom);

            multiUserChatManager.addInvitationListener((conn, room, inviter, reason, password, message, invitation) -> {
                String roomId = room.getRoom().getLocalpart().toString();
                logger.info("Received invitation to room: " + roomId + " - passcode: " + password + " - " + reason);
                joinRoom(new RoomProperties(roomId, password, nickname));
            });

            setMessageScheduler(5000L);

        } catch (XMPPException e) {
            throw new RuntimeException("XMPP Error", e);
        } catch (SmackException e) {
            throw new RuntimeException("Smack Error", e);
        } catch (IOException e) {
            throw new RuntimeException("IO Error", e);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Join a room
     *
     * @param room room to join (part before the "@conference_host"
     */
    private void joinRoom(RoomProperties room) {
        logger.info("Join room \"{}\"", room.getRoomId());

        if (groupChatsByRoomId.containsKey(room.getRoomId())) {
            logger.info("Already in this room");
        } else {
            try {
                lock.lock();
                EntityBareJid jid = JidCreate.entityBareFrom(room.getRoomId() + "@conference." + service);
                MultiUserChat muc = multiUserChatManager.getMultiUserChat(jid);
                muc.join(Resourcepart.from(room.getNickName()), room.getPasscode());
                groupChatsByRoomId.put(room.getRoomId(), muc);
                logger.info("Room joined");

            } catch (Exception e) {
                logger.error("Error joining room: " + e.getMessage());
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Set schedule for sending a message to a multi user chat
     *
     * @param interval the interval time (ms)
     */
    private void setMessageScheduler(long interval) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                DateFormat dateTimeFormat = DateFormat.getDateTimeInstance();
                String message = "Date: " + dateTimeFormat.format(Calendar.getInstance().getTime());
                sendMessageToAllGroups(message);
            }
        }, Calendar.getInstance().getTime(), interval);
    }

    private void sendMessageToAllGroups(String message) {
        for (String roomId : groupChatsByRoomId.keySet()) {
            sendMessage(groupChatsByRoomId.get(roomId), message);
        }
    }

    /**
     * Send a message to a multi user chat
     *
     * @param chat    the MultiUserChat to send the message to
     * @param message the message to send
     */
    private void sendMessage(MultiUserChat chat, String message) {
        try {
            chat.sendMessage(message);
        } catch (SmackException.NotConnectedException | InterruptedException ex) {
            logger.error("Error sending message to room {}", chat.getNickname(), ex);
        }
    }

    /**
     * Send a message to a multi user chat
     *
     * @param chat the MultiUserChat to send the message to
     * @param jid  the joining id to send
     */
    public void sendInvitation(MultiUserChat chat, String jid) {
        try {
            chat.invite(JidCreate.entityBareFrom(jid), "join");
        } catch (SmackException.NotConnectedException | InterruptedException | XmppStringprepException ex) {
            logger.error("Error sending message to room {}", chat.getNickname(), ex);
        }
    }

    /**
     * Waits until the bot exits before returning
     */
    public void waitForExit() {
        lock.lock();
        try {
            running.await();
            connection.disconnect();
            logger.info("Exit successful");
        } catch (InterruptedException ignored) {

        } finally {
            lock.unlock();
        }
    }

    /**
     * Tell the bot to shutdown.
     * This will cause the call to waitForExit to return.
     */
    public void exit() {
        lock.lock();
        try {
            running.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        logger.info("Connected to server");
    }

    @Override
    public void authenticated(XMPPConnection xmppConnection, boolean b) {
        logger.info("Authentication successful");
    }

    @Override
    public void connectionClosed() {
        logger.info("Connection closed");
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        logger.error("Connection was closed because of an error", e);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public String userIdToJid() {
        return username + "@" + service;
    }

}
