package org.thoughtcrime.securesms.additions;

import android.content.Context;

import org.thoughtcrime.securesms.util.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by MayBell on 20.07.2017.
 */

public class NewContactsList {
    private List<QrData> newContacts;

    public NewContactsList() {
        if(this.newContacts == null) {
            this.newContacts = new ArrayList<>();
        }
    }

    public static void addNewContact(final Context context, QrData qrData) {
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
            ncList.newContacts.add(new QrData(ownId, otherId, mobileNr));
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

    public static QrData getNewContactById(final Context context, UUID id) {
        QrData result = null;
        NewContactsList ncList = getNewContactsContent(context);
        if (ncList != null && ncList.newContacts.size() > 0) {
            for(QrData qrd : ncList.newContacts) {
                if(qrd.getOtherId().toString().equals(id.toString())) {
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

    public List<QrData> getNewContacts() {
        return  this.newContacts;
    }
}
