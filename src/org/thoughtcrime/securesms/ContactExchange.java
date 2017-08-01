package org.thoughtcrime.securesms;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.additions.NewContactsList;
import org.thoughtcrime.securesms.additions.QrData;
import org.thoughtcrime.securesms.additions.VCard;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.ServiceUtil;

import java.io.File;
import java.util.UUID;

public class ContactExchange extends AppCompatActivity {

    //Steffi: intent extra data, um fingerprint erhalten zu können
    public static final String SCAN_HELP_EXTRA = "scan_help";
    public static final String NEEDS_FINISH_EXTRA = "needs_finish";
    public static final String LAST_STATE_EXTRA = "last_state";

    private static final String TAG = ContactExchange.class.getSimpleName();

    private static final int QR_READ_STORAGE = 1;

    private static final int ACTIVITY_RESULT_QR_DROID_ENCODE = 5;
    private static final int ACTIVITY_RESULT_QR_DROID_SCAN = 3;
    private int size = 0;
    private int scanHelp = 0;
    private int lastState = 0;
    private boolean needsFinish = false;

    private TextView helpText;
    private Button scanButton;
    private Button finishButton;

    private NewContactsList ncList;
    private UUID uuid;
    private QrData qrData;
    private String remoteNumber;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ncList = NewContactsList.getNewContactsContent(getApplicationContext());
        uuid = UUID.randomUUID();

//        scanHelp = getIntent().getIntExtra(SCAN_HELP_EXTRA, 0);
//        needsFinish = getIntent().getBooleanExtra(NEEDS_FINISH_EXTRA, false);
//        lastState = getIntent().getIntExtra(LAST_STATE_EXTRA, 0);

        Log.d("CE", "Creating Contact Exchange");
        // Steffi: verhindert, dass ein Screenshot gemacht wird
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Holen des möglichen Fingerprints

        setContentView(R.layout.activity_contact_exchange);

        if(!isNetworkConnected()) {
            handleNoConnectivity();
        }

        // Steffi: Permissions schon bei der Installation bzw. Registrierung einholen zB checkSelfPermission(Manifest.permission.QR_READ_STORAGE);
        finishButton = (Button) findViewById(R.id.button_finish);
        setupFinishClickListener();

        scanButton = (Button) findViewById(R.id.button_scan);
        VCard vCard = VCard.getVCard(getApplicationContext());
        String localNumber = vCard.getMobileNumber().trim();
        qrData = new QrData(uuid, null, null);
        String qrCode = String.format("%1$s|%2$s", localNumber, qrData.getOwnId());

        displayQrCode(qrCode);
    }

    private void handleNoConnectivity() {
        final Intent listActivity = new Intent(this, ConversationListActivity.class);
        final Intent wirelessActivity = new Intent(Settings.ACTION_WIRELESS_SETTINGS);

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Keine Netzwerkverbindung gefunden");

        alertDialog.setMessage("Bitte aktiviere eine Verbindung zum Internet. Dies kann eine mobile Datenverbindung oder eine W-LAN Verbinung sein.");

        alertDialog.setPositiveButton("Netzwerkeinstellungen", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(wirelessActivity);
            }
        });

        alertDialog.setNegativeButton("Abbrechen", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startActivity(listActivity);
                finish();
            }
        });

        alertDialog.show();
    }

    private void setupFinishClickListener() {
        finishButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (remoteNumber != null
                        && !remoteNumber.isEmpty()
                        && uuid != null)
                {
                    // TODO Steffi: evtl. VCard senden
                    String checkMessage = String.format("!@check_%s", uuid.toString());
                    sendMessage(checkMessage, remoteNumber);
                } else {
                    // TODO Steffi: zeige Fehlermeldung an
                }
            }
        });
    }

    private void displayQrCode(String qrCode) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;

        size = height > width ? width : height;
        size = Math.round(0.9F * size);

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, QR_READ_STORAGE);
        } else {

            // Steffi: QR Droid als Ziel des Intends festlegen
            Intent qrDroid = new Intent("la.droid.qr.encode");
            //Text für den QR-Code festlegen
            qrDroid.putExtra("la.droid.qr.code", qrCode);
            qrDroid.putExtra("la.droid.qr.image", true);
            // Größe des QR-Codes festlegen
            qrDroid.putExtra("la.droid.qr.size", size);
            // Intend abschicken und Ergebnis abwarten
            try {
                startActivityForResult(qrDroid, ACTIVITY_RESULT_QR_DROID_ENCODE);
            } catch (ActivityNotFoundException activity) {
                activity.printStackTrace();
                Log.e(TAG, "Error while encoding qrCode");
            }

            setupScanClickListener();

            showHelpMessage(ScanState.START);
        }
    }

    private void setupScanClickListener() {
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent qrDroid = new Intent("la.droid.qr.scan");
                qrDroid.putExtra("la.droid.qr.complete", true);

                try {
                    startActivityForResult(qrDroid, ACTIVITY_RESULT_QR_DROID_SCAN);
                } catch (ActivityNotFoundException activity) {
                    activity.printStackTrace();
                    Log.e(TAG, "Error while scanning qr code");
                }
            }
        });
    }

    // Steffi: Überprüfung, ob die Rechte gegeben wurden oder nicht.
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            // Steffi: Prüfung, ob Rechte für externen Speicher gewährt wurden oder nicht
            case QR_READ_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Wenn gewährt, fahre fort
                } else {
                    // wenn nicht gewährt, dann breche ab
                    Toast.makeText(this, "Please grant external storage permission to use this app", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(ContactExchange.this, ConversationActivity.class);
                    startActivity(intent);
                }
        }
    }

    private void showScanButton() {
        this.finishButton.setVisibility(View.INVISIBLE);
        this.scanButton.setVisibility(View.VISIBLE);
    }

    private void showFinishButton() {
        this.scanButton.setVisibility(View.INVISIBLE);
        this.finishButton.setVisibility(View.VISIBLE);
    }

    private void showHelpMessage(ScanState scanState) {

        helpText = (TextView) findViewById(R.id.helpTextView);
        String infoMessage = "";

        switch (scanState) {
            case END:
                infoMessage = "2. Sobald ihr gegenseitig die QR-Codes gescannt habt, kannst du auf \"Fertig\" drücken.";
                break;
            case START:
            default:
                infoMessage = "1. Gegenseitig die QR-Codes scannen";
                break;
        }

        helpText.setText(infoMessage);
        if (needsFinish) {
            Toast t = Toast.makeText(this, "Der Austausch ist fertig!", Toast.LENGTH_LONG);
            t.show();
        }
    }

    @Override
    /**
     * Verarbeitet die von QR Droid zurückgeschickten Daten
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Bei einem Ergebnis wird unterschieden, für welches Intend das Ergebnis geliefert wird
        if (ACTIVITY_RESULT_QR_DROID_SCAN == requestCode && null != data && data.getExtras() != null) {
            // Ergebnis wird ausgelesen
            String result = data.getExtras().getString("la.droid.qr.result");

            Log.d("ContactExchange, Text: ", result);
            String[] stringResults = result.split("\\|");
            if (stringResults != null && stringResults.length > 0) {
                // Nummer aus dem ersten Item des Arrays nutzen, um vCard zu versenden
                if (stringResults[0] != null) {
                    String mobileNumber = stringResults[0];

                    if(stringResults.length >= 2 && !stringResults[1].isEmpty()) {
                        // Steffi: zweites Item muss UUID des gescannten QrCodes sein
                        UUID otherId = UUID.fromString(stringResults[1]);
                        qrData.setMobileNumber(mobileNumber);
                        this.remoteNumber = mobileNumber;

                        qrData.setOtherId(otherId);
                        String qrDataString = String.format("%1$s|%2$s|%3$s", uuid.toString(), mobileNumber, otherId.toString());
                        NewContactsList.addNewContact(getApplicationContext(), qrDataString);

                        // Steffi: Passe Hilfsnachricht an
                        showHelpMessage(ScanState.END);
//                        Steffi: Aktiviere "Fertig-Button"
                        showFinishButton();
                    } else {
                        Log.w(TAG, String.format("Fehler im QR Code: %s", result));
                        // TODO Steffi: throw Error or something
                    }
                }
            }
        }

        if (ACTIVITY_RESULT_QR_DROID_ENCODE == requestCode && null != data && data.getExtras() != null) {
            ImageView imgResult = (ImageView) findViewById(R.id.img_result);
            String qrCode = data.getExtras().getString("la.droid.qr.result");

            File imgFile = new File(qrCode);


            Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

            imgResult.setImageBitmap(bitmap);
            imgResult.setVisibility(View.VISIBLE);
        }
    }

    private void sendMessage(String messageText, String remoteNumber) {
        final Context context = getApplicationContext();
        Recipients recipients = RecipientFactory.getRecipientsFromString(context, remoteNumber, false);
        final MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);

        // Steffi: subscriptionId ermitteln
        long expiresIn = -1;
        int subscriptionId = 0;

        OutgoingTextMessage message = new OutgoingTextMessage(recipients, messageText, expiresIn, subscriptionId);

        final Intent intent = new Intent(this, ConversationListActivity.class);

        // Steffi: threadId ermitteln
        new AsyncTask<OutgoingTextMessage, Void, Long>() {
            @Override
            protected Long doInBackground(OutgoingTextMessage... messages) {
                // Steffi: threadId setzen
                long threadId = 0;
                return MessageSender.send(context, masterSecret, messages[0], threadId, false, null);
            }

            @Override
            protected void onPostExecute(Long result) {
                startActivity(intent);
                finish();
            }
        }.execute(message);
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = ServiceUtil.getConnectivityManager(this);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if(!isConnected)
            return isConnected;

        boolean isDataConnectionAvailbale =
                activeNetwork.getType() == ConnectivityManager.TYPE_WIFI
                        || activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;

        return isConnected && isDataConnectionAvailbale;
    }

    protected enum ScanState {
        START,
        END
    }
}
