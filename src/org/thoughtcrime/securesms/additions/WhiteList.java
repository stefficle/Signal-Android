package org.thoughtcrime.securesms.additions;

import android.content.Context;

import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.HashMap;


/**
 * Repräsentiert die Liste der erlaubten Kontakte
 */
public class WhiteList {
    // Steffi: (Key)String ist mobileNumber, (Value)String ist displayName -> displayName später für Signal displayName notwendig
    private HashMap<String, String> contactList;

    public WhiteList(HashMap<String, String> contactList) {
        this.contactList = contactList;
    }

    public WhiteList() {
        if (this.contactList == null) {
            this.contactList = new HashMap<>();
        }
    }

    /**
     * Fügt einen Kontakt der Whitelist hinzu
     *
     * @param context     Context der Application
     * @param number      Mobilnummer des Kontaktes
     * @param displayName Anzeigename des Kontaktes
     */
    public static void addNumberToFile(final Context context, String number, String displayName) {
        BlackList.removeNumberFromFile(context, number);
        // TODO Steffi: auch von PendingList entfernen?
        try {
            WhiteList whiteList = getWhiteListContent(context);
            whiteList.contactList.put(number, displayName);
            String jsonString = JsonUtils.toJson(whiteList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.whiteListFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Entfernt einen Kontakt aus der Whitelist
     * @param context Context der Application
     * @param number Mobilnummer des Kontaktes
     */
    public static void removeNumberFromFile(final Context context, String number) {
        try {
            WhiteList whiteList = getWhiteListContent(context);
            whiteList.contactList.remove(number);
            String jsonString = JsonUtils.toJson(whiteList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.whiteListFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Liefert den Inhalt der Whitelist zurück.
     * @param context Context der Application
     * @return Inhalt der Whitelist
     */
    public static WhiteList getWhiteListContent(final Context context) {
        WhiteList whiteList = new WhiteList();
        String jsonString = FileHelper.readDataFromFile(context, FileHelper.whiteListFileName);
        try {
            whiteList = JsonUtils.fromJson(jsonString, WhiteList.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return whiteList;
    }

    /**
     * Prüf-Methode, ob eine Mobilnummer in der Whitelist vorhanden ist.
     * @param mobileNumber Mobilnummer, die geprüft werden soll
     * @return TRUE, wenn die Mobilnummer in der Whitelist vorhanden ist, ansonsten FALSE.
     */
    public boolean isInWhiteList(String mobileNumber) {
        return this.contactList.containsKey(mobileNumber);
    }

    public HashMap<String, String> getContactList() {
        return contactList;
    }
}