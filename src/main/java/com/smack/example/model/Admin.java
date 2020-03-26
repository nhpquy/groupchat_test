package com.smack.example.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.Occupant;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.xdata.Form;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Admin implements ConnectionListener {

    private static final Logger logger = LogManager.getLogger(Admin.class);

    private static String host = "msg.beowulfchain.com";
    private static int port = 443;
    private static String service = "beowulfchain.com";
    private static int connectionTimeout = 300000; //mills
    private static int packageTimeout = 120000; //mills
    private static int pingTimeout = 5; // seconds
    public static String trackDir = "./track";

    private final Lock lock;
    private final Condition running;
    private final AbstractXMPPConnection connection;
    private MultiUserChatManager multiUserChatManager;
    private PingManager pingManager;

    private String username;
    private String password;
    private String nickname;

    private List<RoomProperties> createdRooms;
    private ConcurrentHashMap<String, MultiUserChat> groupChatsByRoomId;
    private ConcurrentHashMap<String, BufferedWriter> trackersByRoomId;

    private Timer timer;

    public Admin(String username, String password, String nickname, String roomsString) throws XmppStringprepException {

        lock = new ReentrantLock();
        running = lock.newCondition();

        this.username = username;
        this.password = password;
        this.nickname = nickname;

        this.connection = new XMPPTCPConnection(initSmackConfig());

        // Load created rooms
        if (roomsString == null) {
            createdRooms = new ArrayList<>();
        } else {
            createdRooms = getCreatedRooms(roomsString);
        }

        groupChatsByRoomId = new ConcurrentHashMap<>();
        trackersByRoomId = new ConcurrentHashMap<>();

        File directory = new File(trackDir);
        if (!directory.exists()) {
            directory.mkdir();
        }
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
                .setConnectTimeout(connectionTimeout)
                .build();
    }

    private List<RoomProperties> getCreatedRooms(String roomsString) {
        return null;
    }

    /**
     * Connect to the server
     */
    public void start() {
        try {
            if (connection.isConnected()) return;

            connection.setReplyTimeout(packageTimeout);
            connection.connect();

            if (connection.isConnected())
                logger.info("Smack Message Client connected to server: " + host + ":" + port);

            connection.login();

            if (connection.isAuthenticated())
                logger.info("Smack Message Client authenticated: username=" + username + "; password=XXXXXXXXXXXX");

            connection.addConnectionListener(this);

            pingManager = PingManager.getInstanceFor(connection);
            pingManager.setPingInterval(pingTimeout);

            ReconnectionManager reConnectManager = ReconnectionManager.getInstanceFor(connection);
            reConnectManager.enableAutomaticReconnection();
            reConnectManager.setReconnectionPolicy(ReconnectionManager.ReconnectionPolicy.FIXED_DELAY);
            reConnectManager.setFixedDelay(10);

            multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);

            createdRooms.forEach(this::joinRoom);

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

    private boolean isConnected() throws SmackException.NotConnectedException, InterruptedException {
        return pingManager.pingMyServer();
    }

    /**
     * Create and join a conference room
     *
     * @param room      room info for create (part before the "@conference_host"
     * @param needTrack flag for tracking message in new room
     */
    public MultiUserChat createRoom(RoomProperties room, boolean needTrack) {
        logger.info("Create room \"{}\"", room.getRoomId());

        try {
            if (!groupChatsByRoomId.containsKey(room.getRoomId())) {
                lock.lock();

                EntityBareJid jid = JidCreate.entityBareFrom(roomIdToJid(room.getRoomId()));
                MultiUserChat muc = multiUserChatManager.getMultiUserChat(jid);
                muc.create(Resourcepart.from(room.getNickName()));

                Form form = muc.getConfigurationForm().createAnswerForm();
                form.setAnswer("muc#roomconfig_roomname", room.getRoomName());
                form.setAnswer("muc#roomconfig_persistentroom", true);
                form.setAnswer("muc#roomconfig_publicroom", false);
                form.setAnswer("muc#roomconfig_passwordprotectedroom", true);
                form.setAnswer("muc#roomconfig_maxusers", Collections.singletonList("2000"));
                form.setAnswer("muc#roomconfig_roomsecret", room.getPasscode());
                muc.sendConfigurationForm(form);

                createdRooms.add(room);

                joinRoom(room);

                if (needTrack) createTracker(room.getRoomId());

                muc.addMessageListener(message -> handleTrackChatGroup(room.getRoomId(), message, needTrack));

                return muc;
            }
            logger.info("Already exist this room \"{}\"", room.getRoomId());
        } catch (Exception e) {
            logger.error("Error create room with room id \"{}\"", room.getRoomId());
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return null;
    }

    public String userIdToJid() {
        return username + "@" + service;
    }

    public String roomIdToJid(String roomId) {
        return roomId + "@conference." + service;
    }

    /**
     * Invite list of users to group chat
     *
     * @param groupChat the MultiUserChat group chat to send the message to
     * @param jids      the list jid of users
     * @param reason    the message in invitation
     * @return list of user can not join the room
     */
    public List<String> inviteUsers(MultiUserChat groupChat, List<String> jids, String reason) {
        List<String> errors = new ArrayList<>();

        if (groupChat != null && jids != null) {
            for (String userJID : jids) {
                String inviteErr = sendInvitation(groupChat, userJID, reason);
                if (inviteErr != null) {
                    errors.add(inviteErr);
                }
            }
        }

        return errors;
    }

    public String sendInvitation(MultiUserChat groupChat, String userJID, String reason) {
        try {
            groupChat.invite(JidCreate.entityBareFrom(userJID), reason);
        } catch (Exception e) {
            String error = String.format("Error invite user to room user %s - error %s", userJID, e);
            logger.error(error);
            return userJID;
        }
        return null;
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
                EntityBareJid jid = JidCreate.entityBareFrom(roomIdToJid(room.getRoomId()));
                groupChat = multiUserChatManager.getMultiUserChat(jid);
                groupChat.join(Resourcepart.from(nickname), room.getPasscode());
                groupChatsByRoomId.put(room.getRoomId(), groupChat);
                logger.info("\"{}\" joined room \"{}\"", nickname, room.getRoomId());
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
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (!isConnected()) return;
                    DateFormat dateTimeFormat = DateFormat.getDateTimeInstance();
                    String message = "Date: " + dateTimeFormat.format(Calendar.getInstance().getTime());
                    sendMessageToAllGroups(message);
                } catch (SmackException.NotConnectedException | InterruptedException ignored) {

                }
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
     * Create new message tracker for group chat
     *
     * @param roomId the id of room need to track
     */
    private void createTracker(String roomId) {
        try {
            lock.lock();
            if (!trackersByRoomId.containsKey(roomId)) {

                String fileName = trackDir + "/" + roomId;
                File file = new File(fileName);
                if (!file.createNewFile()) {
                    file.createNewFile();
                }

                FileWriter fw = new FileWriter(file, true);
                BufferedWriter tracker = new BufferedWriter(fw);

                trackersByRoomId.put(roomId, tracker);
            }
        } catch (Exception e) {
            logger.error("Error when create chat group file tracker");
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void handleTrackChatGroup(String roomId, Message message, boolean needTrack) {

        String record = String.format("%s: %s\n", message.getFrom().toString(), message.getBody());

        if (needTrack) {
            try {
                if (!trackersByRoomId.containsKey(roomId)) {
                    createTracker(roomId);
                }
                trackersByRoomId.get(roomId).write(record);
            } catch (Exception e) {
                logger.error("Error when write chat record to file tracker");
                e.printStackTrace();
            }
        } else {
            logger.debug(record);
        }
    }

    /**
     * Waits until the bot exits before returning
     */
    public void waitForExit() {
        lock.lock();
        try {
            connection.disconnect();

            for (String roomId : trackersByRoomId.keySet()) {
                trackersByRoomId.get(roomId).close();
            }
            trackersByRoomId.clear();
            logger.info("Exit successful");

        } catch (IOException e) {
            e.printStackTrace();
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
        exit();
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

    public List<String> getMembers(String roomId) throws Exception {
        List<String> users = new ArrayList<String>();

        if (groupChatsByRoomId.containsKey(roomId)) {
            for (Occupant occupant : groupChatsByRoomId.get(roomId).getParticipants()) {
                users.add(occupant.getJid().toString());
            }
        }

        return users;
    }

    public Integer getMembersCount(String roomId) {
        if (groupChatsByRoomId.containsKey(roomId)) {
            return groupChatsByRoomId.get(roomId).getOccupantsCount();
        }
        return 0;
    }

    public List<RoomProperties> getCreatedRooms() {
        return createdRooms;
    }
}
