package org.thoughtcrime.securesms.additions;


/**
 * Hilfs-Klasse um Spezial-Kommandos aus einer Nachricht zu interpretieren.
 */
// Steffi: List-Kommando darf nur "erlaubt","block" oder "wartet" beinhalten als Listennamen
public final class MessageHelper {
    // Steffi: Konventionen ->
    // "!@ COMMAND PARAMS"
    // "!@ ok ID"
    // "!@ block ID (perm)"
    // "!@ new NUMBER DISPLAYNAME"
    // "!@ list LISTNAME"
    // "!@ help"
    // "!@ remove Number"

    /**
     * Prefix, um ein Kommando zu identifizieren
     */
    private static final String SPECIAL_CODE_PREFIX = "!@";
    private static final String SPECIAL_DEBUG_PREFIX = "!#";

    /**
     * Prüf-Methode, ob die Nachricht ein Spezial-Kommando ist.
     *
     * @param message Die Nachricht, die überprüft werden soll.
     * @return TRUE, wenn die Nachricht ein SPezial-Kommando ist, ansonsten FALSE.
     */
    public static boolean startsWithSpecialCode(String message) {
        return message.startsWith(SPECIAL_CODE_PREFIX) || message.startsWith(SPECIAL_DEBUG_PREFIX);
    }

    /**
     * Liefert die Nummer aus der Nachricht mit einem Spezial-Kommando zurück
     * @param message Die Nachricht, aus der die Nummer ermittelt werden soll
     * @return Liefert die Nummer aus der Nachricht zurück.
     */
    public static String getNumberFromMessage(String message) {
        String[] messageParts = getMessageParts(message);
        String number = "";
        if (messageParts.length > 2) {
            number = messageParts[1];
        }
        return number;
    }

    /**
     * Liefert die Nummer aus der Nachricht mit einem Spezial-Kommando zurück
     *
     * @param message Die Nachricht, aus der die Nummer ermittelt werden soll
     * @return Liefert die Nummer aus der Nachricht zurück.
     */
    public static String getNumberFromMessageForRemoval(String message) {
        String[] messageParts = getMessageParts(message);
        String number = "";
        if (messageParts.length > 1 && messageParts[1].startsWith("+")) {
            number = messageParts[1];
        }
        return number;
    }

    public static String getNumberForDebug(String message) {
        String[] messageParts = getDebugParts(message);
        String number = "";
        if (messageParts.length > 1) {
            number = messageParts[1];
        }
        return number;
    }

    public static String getFirstNameForDebug(String message) {
        String[] messageParts = getDebugParts(message);
        String firstName = "";
        if (messageParts.length > 2) {
            firstName = messageParts[2];
        }
        return firstName;
    }

    public static String getLastNameForDebug(String message) {
        String[] messageParts = getDebugParts(message);
        String lastName = "";
        if (messageParts.length > 3) {
            lastName = messageParts[3];
        }
        return lastName;
    }

    /**
     * Liefert den Anzeigenamen aus der Nachricht mit einem Spezial-Kommando zurück
     * @param message Die Nachricht, aus der der Anzeigenamen ermittelt werden soll
     * @return Liefert den Anzeigenamen aus der Nachricht zurück.
     */
    public static String getDisplayNameFromMessage(String message) {
        String[] messageParts = getMessageParts(message);
        String displayName = "";
        if (messageParts.length > 3) {
            displayName = messageParts[2];
            if (messageParts.length == 4) {
                displayName += " " + messageParts[3];
            }
        }
        return displayName;
    }

    private static String[] getMessageParts(String message) {
        // Steffi: Prefix wird entfernt und getrimmt.
        String newMessage = message.replaceAll(SPECIAL_CODE_PREFIX, "").trim();
        // Splittet die Nachricht in ein String-Array nach Leerzeichen auf
        return newMessage.split(" ");
    }

    private static String[] getDebugParts(String message) {
        String newDebugMessage = message.replaceAll(SPECIAL_DEBUG_PREFIX, "").trim();
        return newDebugMessage.split(" ");
    }

    /**
     * Liefert die ID aus der Nachricht mit einem Spezial-Kommando zurück
     * @param message Die Nachricht, aus der die ID ermittelt werden soll
     * @return Liefert die ID aus der Nachricht zurück.
     */
    public static Integer getIdFromMessage(String message) {
        Integer id = 0;

        String[] messageParts = getMessageParts(message);

        if (messageParts.length > 1) {
            try {
                id = Integer.valueOf(messageParts[messageParts.length - 1]);
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        }

        return id;
    }

    /**
     * Liefert das Spezial-Kommando zurück
     * @param message Die Nachricht, aus der das Kommando ermittelt werden soll
     * @return Liefert das Kommando aus der Nachricht zurück.
     */
    public static String getCommandFromMessage(String message) {
        String specialCommand = "";

        String[] messageParts = getMessageParts(message);

        if (messageParts.length > 0) {
            specialCommand = messageParts[0];
        }

        return specialCommand.toLowerCase();
    }

    public static boolean isDebugCommand(String message) {
        return message.startsWith(SPECIAL_DEBUG_PREFIX);
    }

    public static String getDebugCommand(String message) {
        String debugCommand = "";

        String[] messageParts = getDebugParts(message);

        if (messageParts.length > 0) {
            debugCommand = messageParts[0];
        }
        return debugCommand.toLowerCase();
    }

    /**
     * Liefert den Namen der angefragten Liste zurück.
     * @param message Die Nachricht, aus der der Listenname identifiziert werden soll
     * @return Liefert den Namen der angefragten Liste zurück.
     */
    public static String getListNameFromMessage(String message) {
        String listName = "";
        String[] messageParts = getMessageParts(message);
        if (messageParts.length == 2) {
            listName = messageParts[1].toLowerCase();
        }
        return listName;
    }

    /**
     * Überprüft, ob es sich um eine permanente Sperrung eines Kontaktes handelt.
     * @param message Die Nachricht, die auf die permanente Sperrung überprüft werden soll
     * @return TRUE, wenn es sich um eine permanente Sperrung handelt, ansonsten FALSE.
     */
    public static boolean isPermanentBlock(String message) {
        String[] messageParts = getMessageParts(message);

        return messageParts.length == 3 && message.endsWith(" perm");
    }
}
