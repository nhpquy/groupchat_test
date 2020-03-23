package com.smack.example.utils;


public class XMPPExceptionUtils {
    /**
     * Returns an explanation for the exception.
     *
     * @param ex the <code>XMPPException</code>
     * @return the reason for the exception.
     */
    /*public static String getReason(XMPPException ex) {
        String reason = "";
        int code = 0;
        if (ex.getXMPPError() != null) {
            code = ex.getXMPPError().getCode();
        }

        if (code == 0) {
            reason = "No response from server.";
        }
        else if (code == 401) {
            reason = "The password did not match the room's password.";
        }
        else if (code == 403) {
            reason = "You have been banned from this room.";
        }
        else if (code == 404) {
            reason = "The room you are trying to enter does not exist.";
        }
        else if (code == 405) {
            reason = "You do not have permission to create a room.";
        }
        else if (code == 407) {
            reason = "You are not a member of this room.\nThis room requires you to be a member to join.";
        }

        return reason;
    }*/
}
