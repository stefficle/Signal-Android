package org.thoughtcrime.securesms.additions;

import android.content.Context;

import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


/**
 * Repräsentiert die Liste der geblockten Kontakte
 */
public class BlackList {
    // Steffi: Anzahl der Tage, bis der Kontakt aus der Blacklist entfernt werden kann
    private static final Integer EXPIRATION_TIME = 14;
    // Steffi: (Key)String ist mobileNumber, (Value)Date ist Ablaufdatum
    private HashMap<String, Date> blockedContacts;

    public BlackList(HashMap<String, Date> blockedContacts) {
        this.blockedContacts = blockedContacts;
    }

    public BlackList() {
        if (this.blockedContacts == null) {
            this.blockedContacts = new HashMap<>();
        }
    }

    /**
     * Methode um eine Mobilnummer zur Blacklist hinzuzufügen
     *
     * @param context        Context der Application
     * @param number         Mobilnummer die hinzugefügt werden soll
     * @param expirationDate Ablaufdatum
     */
    public static void addNumberToFile(final Context context, String number, Date expirationDate) {
        WhiteList.removeNumberFromFile(context, number);
        try {
            BlackList blackList = getBlackListContent(context);
            blackList.blockedContacts.put(number, expirationDate);
            String jsonString = JsonUtils.toJson(blackList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.blackListFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Methode, um ein Ablaufdatum für die Blacklist zu erhalten
     * @return Liefert ein Date-Objekt zurück, welches Anzahl der EXPIRATION_TIME in Tagen in der Zukunft liegt.
     */
    public static Date getExpirationDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.DATE, EXPIRATION_TIME);
        return cal.getTime();
    }

    /**
     * Entfernt eine Nummer aus der Blacklist
     * @param context Context der Application
     * @param number Mobilnummer, die aus der Blacklist entfernt werden soll.
     */
    public static void removeNumberFromFile(final Context context, String number) {
        try {
            BlackList blackList = getBlackListContent(context);
            blackList.blockedContacts.remove(number);
            String jsonString = JsonUtils.toJson(blackList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.blackListFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Hilfs-Methode um die Blacklist auf veraltete Einträge zu überprüfen
     * @param context Context der Application
     */
    public static void checkExpirationDates(final Context context) {
        String jsonString = FileHelper.readDataFromFile(context, FileHelper.blackListFileName);
        try {
            BlackList blackList = JsonUtils.fromJson(jsonString, BlackList.class);
            ArrayList<String> numbersToRemove = new ArrayList<>();
            Date now = new Date();

            // Steffi: Gehe blockierte Kontakte durch und überprüfe Ablaufdatum mit aktuellem Datum
            for (Map.Entry<String, Date> bContact : blackList.blockedContacts.entrySet()) {
                // Wenn kein Datum angegeben, gehe zum nächsten Eintrag
                if (bContact.getValue() == null) continue;

                // Wenn Ablaufdatum kleiner als aktuelles Datum ist, dann markiere die Nummer zum Entfernen
                if (bContact.getValue().before(now)) {
                    numbersToRemove.add(bContact.getKey());
                }
            }

            // Entferne die markierten Nummern aus der blackList
            for (String ntr : numbersToRemove) {
                blackList.blockedContacts.remove(ntr);
            }

            jsonString = JsonUtils.toJson(blackList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.blackListFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Liefert den Inhalt der Blacklist zurück.
     * @param context Context der Application
     * @return Inhalt der Blacklist
     */
    public static BlackList getBlackListContent(final Context context) {
        String jsonString = FileHelper.readDataFromFile(context, FileHelper.blackListFileName);
        BlackList blackList = new BlackList();
        try {
            blackList = JsonUtils.fromJson(jsonString, BlackList.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return blackList;
    }

    public HashMap<String, Date> getBlockedContacts() {
        return blockedContacts;
    }

    /**
     * Prüf-Methode, ob die angegebene Nummer in der Blacklist vorhanden ist.
     * @param mobileNumber Die Nummer, die geprüft werden soll
     * @return TRUE, wenn sie in der Blacklist vorhanden ist. Andernfalls FALSE.
     */
    public boolean isInBlackList(String mobileNumber) {
        return this.blockedContacts.containsKey(mobileNumber);
    }
}
