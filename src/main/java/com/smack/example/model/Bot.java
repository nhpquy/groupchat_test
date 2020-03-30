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
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Bot implements Runnable, ConnectionListener, ReconnectionListener, PingFailedListener {

    private Logger logger;

    private static final String host = "msg.beowulfchain.com";
    private static final int port = 443;
    private static final String service = "beowulfchain.com";
    private static final int connectionTimeout = 5000; //mills
    private static final int packageTimeout = 30000; //mills
    private static final int pingInterval = 30; // seconds

    private final Lock lock;
    private final Condition running;

    private boolean connected;
    private boolean reconnected;
    private AbstractXMPPConnection connection;
    private MultiUserChatManager multiUserChatManager;

    private String username;
    private String password;
    private String nickname;

    private List<RoomProperties> joinedRooms;
    private Map<String, MultiUserChat> groupChatsByRoomId;

    private final ScheduledExecutorService timer;

    public Bot(String username, String password, String nickname, String roomsString) {

        lock = new ReentrantLock();
        running = lock.newCondition();
        connected = false;

        this.username = username;
        this.password = password;
        this.nickname = nickname;

        logger = LogManager.getLogger(nickname);

        // Load joined rooms
        if (roomsString == null) {
            joinedRooms = new ArrayList<>();
        } else {
            joinedRooms = getJoinedRooms(roomsString);
        }

        groupChatsByRoomId = new HashMap<>();
        timer = Executors.newSingleThreadScheduledExecutor();
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
                .setConnectTimeout(connectionTimeout)
                .build();
    }

    private List<RoomProperties> getJoinedRooms(String roomsString) {
        return null;
    }

    @Override
    public void run() {
        logger.info("I'm " + nickname);
        start();
        setMessageScheduler(5);
    }

    /**
     * Connect to the server
     */
    private void start() {
        try {
            connect();

            ChatManager chatManager = ChatManager.getInstanceFor(connection);
            chatManager.addIncomingListener((EntityBareJid from, Message message, Chat chat) -> {
                logger.info("Received private message: " + message.getBody());
            });

            connection.addConnectionListener(this);

            PingManager pingManager = PingManager.getInstanceFor(connection);
            pingManager.setPingInterval(pingInterval);
            pingManager.registerPingFailedListener(this);

            ReconnectionManager reconnectionManager = ReconnectionManager.getInstanceFor(connection);
            reconnectionManager.enableAutomaticReconnection();
            reconnectionManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY);
            reconnectionManager.setFixedDelay(10);
            reconnectionManager.addReconnectionListener(this);

            multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);

            joinedRooms.forEach(this::joinRoom);

            multiUserChatManager.addInvitationListener((conn, room, inviter, reason, password, message, invitation) -> {
                String roomId = room.getRoom().getLocalpart().toString();
                logger.info("Received invitation to room: " + roomId + " - passcode: " + password + " - " + reason);
                joinRoom(new RoomProperties(roomId, password, nickname));
            });

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
     * Connects to XMPP server
     */
    private void connect() throws IOException, InterruptedException, XMPPException, SmackException {

        connection = new XMPPTCPConnection(initSmackConfig());

        if (connection.isConnected()) return;

        connection.setReplyTimeout(packageTimeout);
        connection.connect();

        if (connection.isConnected())
            logger.info("Smack Message Client connected to server: " + host + ":" + port);

        connection.login();

        if (connection.isAuthenticated())
            logger.info("Smack Message Client authenticated: username=" + username);

        connected = true;
    }

    private void updateStatus(boolean status) {
        try {
            lock.lock();
            connected = status;
        } finally {
            lock.unlock();
        }
    }

    private boolean isConnected() {
        return connected;
    }

    /**
     * Join a room
     *
     * @param room room to join (part before the "@conference_host"
     */
    public void joinRoom(RoomProperties room) {
        logger.info("Join room \"{}\"", room.getRoomId());

        MultiUserChat groupChat;
        if ((groupChat = groupChatsByRoomId.get(room.getRoomId())) != null) {
            if (groupChat.isJoined()) {
                logger.info("Already in this room");
            } else {
                try {
                    groupChat.join(Resourcepart.from(nickname), room.getPasscode());
                    logger.info("\"{}\" joined room \"{}\"", nickname, room.getRoomId());
                } catch (Exception e) {
                    logger.error("Error joining room", e);
                }
            }
        } else {
            try {
                lock.lock();
                EntityBareJid jid = JidCreate.entityBareFrom(room.getRoomId() + "@conference." + service);
                groupChat = multiUserChatManager.getMultiUserChat(jid);
                groupChat.join(Resourcepart.from(nickname), room.getPasscode());
                groupChatsByRoomId.put(room.getRoomId(), groupChat);
                logger.info("\"{}\" joined room \"{}\"", nickname, room.getRoomId());
                joinedRooms.add(room);
            } catch (Exception e) {
                logger.error("Error joining room", e);
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
        timer.scheduleWithFixedDelay(() -> {
            if (!isConnected()) return;
            DateFormat dateTimeFormat = DateFormat.getDateTimeInstance();
            String message = "Date: " + dateTimeFormat.format(Calendar.getInstance().getTime());
            sendMessageToAllGroups(message);
        }, interval, interval, TimeUnit.SECONDS);
    }

    /**
     * Send message to all joined room
     *
     * @param message the message to send
     */
    private void sendMessageToAllGroups(String message) {
        for (String roomId : groupChatsByRoomId.keySet()) {
            logger.debug("\"{}\" send message to room \"{}\"", nickname, roomId);
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
            logger.error("Error sending message to room {}", chat.getNickname());
            connected = false;
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
    public void beforeForExit() {
        lock.lock();
        try {
            timer.shutdown();
            connection.disconnect();
            logger.info("Exit successful");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        logger.info("Connected to server");
        connected = true;
        if (reconnected) {
            joinedRooms.forEach(this::joinRoom);
        }
    }

    @Override
    public void authenticated(XMPPConnection xmppConnection, boolean b) {
        logger.info("Authentication successful");
        connected = true;
    }

    @Override
    public void connectionClosed() {
        logger.info("Connection closed");
        connected = false;
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        logger.error("Connection was closed because of an error: " + e.getMessage());
        connected = false;
    }

    @Override
    public void reconnectingIn(int i) {
        logger.info("Reconnecting in {} ...", i);
        if (i == 0) reconnected = true;
    }

    @Override
    public void reconnectionFailed(Exception e) {
        logger.info("Reconnection failed! Error: {}", e.getMessage());
    }

    @Override
    public void pingFailed() {
        logger.info("The ping failed, restarting the ping interval again ...");
        PingManager pingManager = PingManager.getInstanceFor(connection);
        pingManager.setPingInterval(pingInterval);
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
