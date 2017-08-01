package org.thoughtcrime.securesms.additions;

import android.content.Context;

import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Created by MayBell on 20.07.2017.
 */

public class NewContactsList {
    private ArrayList<String> newContacts;

    public NewContactsList() {
        if(this.newContacts == null) {
            this.newContacts = new ArrayList<>();
        }
    }

    public static void addNewContact(final Context context, String qrData) {
        try {
            NewContactsList ncList = getNewContactsContent(context);
            ncList.newContacts.add(qrData);
            String jsonString = JsonUtils.toJson(ncList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.newContactsFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addNewContact(final Context context, UUID ownId, String mobileNr, UUID otherId) {
        try {
            NewContactsList ncList = getNewContactsContent(context);
            QrData d = new QrData(ownId, otherId, mobileNr);
            ncList.newContacts.add(String.format("%1$s|%2$s|%3$s", ownId, mobileNr, otherId));
            String jsonString = JsonUtils.toJson(ncList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.newContactsFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static QrData removeNewContact(final Context context, QrData qrData) {
        boolean result = false;
        try {
            NewContactsList ncList = getNewContactsContent(context);
            result = ncList.newContacts.remove(qrData);
            String jsonString = JsonUtils.toJson(ncList);
            FileHelper.writeDataToFile(context, jsonString, FileHelper.newContactsFileName);
        } catch (IOException io) {
            io.printStackTrace();
        }

        return result ? qrData : null;
    }

    public static String getNewContactById(final Context context, String uuid) {
        String result = null;
        NewContactsList ncList = getNewContactsContent(context);
        if (ncList != null && ncList.newContacts.size() > 0) {
            for(String qrd : ncList.newContacts) {
                if(qrd.endsWith(uuid)) {
                    result = qrd;
                    break;
                }
            }
        }
        return result;
    }

    public static NewContactsList getNewContactsContent(final Context context) {
        NewContactsList ncList = new NewContactsList();
        String jsonString = FileHelper.readDataFromFile(context, FileHelper.newContactsFileName);
        try {
            ncList = JsonUtils.fromJson(jsonString, NewContactsList.class);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        return ncList;
    }

    public static boolean isNumberInList(final Context context, String number) {
        NewContactsList ncList = getNewContactsContent(context);

        if(ncList != null && !ncList.newContacts.isEmpty()) {
            for(String newContact : ncList.newContacts) {
                if(newContact.contains(number)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public ArrayList<String> getNewContacts() {
        return  this.newContacts;
    }
}
