package com.smack.example;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.xdata.Form;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.Arrays;
import java.util.HashMap;

public class SmackClient {
    private XMPPTCPConnection connection;
    private ChatManager chatManager;
    private MultiUserChatManager multiUserChatManager;
    private HashMap<String, Chat> chatHashMap;

    public static String host = "msg.beowulfchain.com";
    public static int port = 443;
    public static String service = "beowulfchain.com";
//    private String adminAccount = "bwadmin";
//    private String adminPassword = "PMardMXPeebgDXvG";

    private String username;
    private String password;

    public SmackClient(String username, String password) {
        this.username = username;
        this.password = password;
        init();
    }

    private void init() {
        try {
            System.out.println("Smack Message Client init");

            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                    .setHost(host)
                    .setPort(port)
                    .setUsernameAndPassword(username, password)
                    .setXmppDomain(service)
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
                    .setSendPresence(true)
                    .addEnabledSaslMechanism(Arrays.asList("PLAIN", "X-OAUTH2", "SCRAM-SHA-1"))
//                    .enableDefaultDebugger()
                    .build();

            connection = new XMPPTCPConnection(config);
            connection.connect();

            if (connection.isConnected())
                System.out.println("Smack Message Client connected to server: " + host + ":" + port);

            connection.login();

            if (connection.isAuthenticated())
                System.out.println("Smack Message Client authenticated: username=" + username + "; password=XXXXXXXXXXXX");

            chatManager = ChatManager.getInstanceFor(connection);
            multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);

            if (chatHashMap != null)
                chatHashMap.clear();
            chatHashMap = new HashMap<>();

            chatManager.addIncomingListener((EntityBareJid from, Message message, Chat chat) -> {
                System.out.println("Received private message: " + message.getBody());
            });

            multiUserChatManager.addInvitationListener(new InvitationListener() {
                @Override
                public void invitationReceived(XMPPConnection conn, MultiUserChat room, EntityJid inviter, String reason, String password, Message message, MUCUser.Invite invitation) {
                    System.out.println("Received invitation to room: " + room.getRoom().getLocalpart().toString() + " - passcode: " + password + " - " + reason);
                }
            });

            System.out.println("Smack Message Client init successfully");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isAuthenticated() {
        return connection != null && connection.isAuthenticated();
    }

    private void disconnect() {
        if (connection != null && connection.isConnected())
            connection.disconnect();
        System.out.println("Smack Message Client disconnected");
    }

    private synchronized Chat getChat(String to) {
        to = to + "@" + service;
        Chat chat = null;
        if (chatHashMap.containsKey(to))
            chat = chatHashMap.get(to);
        if (chat == null) {
            try {
                EntityBareJid jid = JidCreate.entityBareFrom(to);
                chatHashMap.put(to, chatManager.chatWith(jid));
                chat = chatHashMap.get(to);
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }

        return chat;
    }

    public void sendMessageAsync(String to, String message) throws Exception {
        if (isAuthenticated()) {
            Chat chat = getChat(to);
            if (chat != null) {
                AsyncHandler.run(() -> {
                    chat.send(message);
                    return null;
                });
            }
        }
    }

    public MultiUserChat createRoom(String roomId, String nickName, String passcode, String roomName) throws Exception {

        EntityBareJid jid = JidCreate.entityBareFrom(roomId + "@conference." + service);
        MultiUserChat muc = multiUserChatManager.getMultiUserChat(jid);
        muc.create(Resourcepart.from(nickName));

        Form form = muc.getConfigurationForm().createAnswerForm();

        form.setAnswer("muc#roomconfig_roomname", roomName);
        form.setAnswer("muc#roomconfig_persistentroom", true);
        form.setAnswer("muc#roomconfig_publicroom", false);
        form.setAnswer("muc#roomconfig_passwordprotectedroom", true);
        form.setAnswer("muc#roomconfig_roomsecret", passcode);
        muc.sendConfigurationForm(form);
        return muc;
    }

    public MultiUserChat joinRoom(String roomId, String passcode, String nickName) {
        try {
            EntityBareJid jid = JidCreate.entityBareFrom(roomId + "@conference." + service);
            MultiUserChat muc = multiUserChatManager.getMultiUserChat(jid);
            muc.join(Resourcepart.from(nickName), passcode);
            return muc;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
