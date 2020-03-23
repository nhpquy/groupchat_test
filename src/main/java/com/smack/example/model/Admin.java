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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Admin implements ConnectionListener {

    private static final Logger logger = LogManager.getLogger(Admin.class);

    public static String host = "msg.beowulfchain.com";
    public static int port = 443;
    public static String service = "beowulfchain.com";

    private final Lock lock;
    private final Condition running;
    private final XMPPTCPConnection connection;
    private MultiUserChatManager multiUserChatManager;

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
            createdRooms = getCreateRooms(roomsString);
        }

        groupChatsByRoomId = new ConcurrentHashMap<>();
        trackersByRoomId = new ConcurrentHashMap<>();
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

    /**
     * Connect to the server
     */
    public void start() {
        try {
            connection.connect();

            if (connection.isConnected())
                logger.info("Smack Message Client connected to server: " + host + ":" + port);

            connection.login();

            if (connection.isAuthenticated())
                logger.info("Smack Message Client authenticated: username=" + username + "; password=XXXXXXXXXXXX");

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

    /**
     * Create and join a conference room
     *
     * @param room room info for create (part before the "@conference_host"
     */
    public MultiUserChat createRoom(RoomProperties room, boolean needTrack) {
        logger.info("Create room \"{}\"", room.getRoomId());

        try {
            if (!groupChatsByRoomId.containsKey(room.getRoomId())) {
                lock.tryLock(3, TimeUnit.SECONDS);
                EntityBareJid jid = JidCreate.entityBareFrom(room.getRoomId() + "@conference." + service);
                MultiUserChat muc = multiUserChatManager.getMultiUserChat(jid);
                muc.create(Resourcepart.from(room.getNickName()));

                Form form = muc.getConfigurationForm().createAnswerForm();

                form.setAnswer("muc#roomconfig_roomname", room.getRoomName());
                form.setAnswer("muc#roomconfig_persistentroom", true);
                form.setAnswer("muc#roomconfig_publicroom", false);
                form.setAnswer("muc#roomconfig_passwordprotectedroom", true);
                form.setAnswer("muc#roomconfig_roomsecret", room.getPasscode());
                muc.sendConfigurationForm(form);

                createdRooms.add(room);

                joinRoom(room);

                if (needTrack) {
                    createTracker(room.getRoomId());
                    muc.addMessageListener(message -> {
                        handleTrackChatGroup(room.getRoomId(), message);
                    });
                }

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

    public static String userIdToJid(String userId) {
        return userId + "@" + service;
    }

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

    private String sendInvitation(MultiUserChat groupChat, String userJID, String reason) {
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
    private void joinRoom(RoomProperties room) {
        logger.info("Join room \"{}\"", room.getRoomId());

        if (groupChatsByRoomId.containsKey(room.getRoomId())) {
            logger.info("Already in this room");
        } else {
            try {
                lock.tryLock(2, TimeUnit.SECONDS);
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

    private void createTracker(String roomId) {
        try {
            if (!trackersByRoomId.containsKey(roomId)) {
                lock.tryLock(2, TimeUnit.SECONDS);
                File file = new File(roomId);
                if (!file.exists()) {
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

    private void handleTrackChatGroup(String roomId, Message message) {
        try {
            String record = String.format("%s: %s\n", message.getFrom().toString(), message.getBody());
            if (!trackersByRoomId.containsKey(roomId)) {
                createTracker(roomId);
            }
            trackersByRoomId.get(roomId).write(record);
        } catch (Exception e) {
            logger.error("Error when write chat record to file tracker");
            e.printStackTrace();
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

            for (String roomId : trackersByRoomId.keySet()) {
                trackersByRoomId.get(roomId).close();
            }
            trackersByRoomId.clear();

        } catch (InterruptedException ignored) {

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

    public List<String> getMembers(String roomID) throws Exception {
        List<String> users = new ArrayList<String>();

        for (Occupant occupant : groupChatsByRoomId.get(roomID).getParticipants()) {
            users.add(occupant.getJid().toString());
        }

        return users;
    }

    public static void main(String[] args) {
        try {
            Admin admin = new Admin("25251325", "123456789", "Admin", null);
            admin.start();

            String roomId = String.valueOf(System.currentTimeMillis());
            String password = "1234";

            RoomProperties newRoom = new RoomProperties(
                    roomId,
                    password,
                    admin.nickname
            );

            MultiUserChat newGroupChat = admin.createRoom(newRoom, true);

            Bot bot1 = new Bot("123456789a", "123456789", "Bot1", null);
            Bot bot2 = new Bot("1234567897", "123456789", "Bot2", null);
            bot1.run();
            bot2.run();

            List<String> jids = new ArrayList<>();
            jids.add(userIdToJid(bot1.getUsername()));
            jids.add(userIdToJid(bot2.getUsername()));

            admin.inviteUsers(newGroupChat, jids, "join");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
