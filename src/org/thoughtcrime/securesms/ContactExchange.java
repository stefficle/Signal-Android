package org.thoughtcrime.securesms;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.thoughtcrime.securesms.additions.FileHelper;
import org.thoughtcrime.securesms.additions.VCard;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.fingerprint.Fingerprint;
import org.whispersystems.libsignal.fingerprint.NumericFingerprintGenerator;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.File;
import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ContactExchange extends AppCompatActivity {

    //Steffi: intent extra data, um fingerprint erhalten zu können
    public static final String FINGERPRINT = "qr_fingerprint";
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scanHelp = getIntent().getIntExtra(SCAN_HELP_EXTRA, 0);
        needsFinish = getIntent().getBooleanExtra(NEEDS_FINISH_EXTRA, false);
        lastState = getIntent().getIntExtra(LAST_STATE_EXTRA, 0);

        Log.d("CE","Creating Contact Exchange");
        // Steffi: verhindert, dass ein Screenshot gemacht wird
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // Holen des möglichen Fingerprints
        String fingerprint = getIntent().getStringExtra(FINGERPRINT);

        setContentView(R.layout.activity_contact_exchange);

        // Steffi: Permissions schon bei der Installation bzw. Registrierung einholen zB checkSelfPermission(Manifest.permission.QR_READ_STORAGE);

        final Button scanButton = (Button) findViewById(R.id.button_scan);
        VCard vCard  = VCard.getVCard(getApplicationContext());
        String localNumber = vCard.getMobileNumber().trim();
        String uniqueId = UUID.randomUUID().toString(); // Steffi: Erzeugung einer unique ID in Form von "067e6162-3b6f-4ae2-a171-2470b63dff00"
//        FileHelper.writeUuid(getApplicationContext(), uniqueId);
        String qrCode = String.format("%1$s|%2$s", localNumber, uniqueId);

        // Prüfen, ob Fingerprint vorhanden, wenn ja, dann in QR Code einarbeiten
        if (fingerprint != null && !fingerprint.isEmpty()) {
            qrCode += String.format("|%s", fingerprint);
        }

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

            showHelpMessage();
        }
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

    private void showHelpMessage() {

        helpText = (TextView) findViewById(R.id.helpTextView);
        String infoMessage = "";

        switch (scanHelp) {
            case 1:
                infoMessage = "2. Jetzt diesen QR-Code scannen lassen und dann selbst noch einmal den anderen QR-Code scannen";
                break;
            case 2:
                infoMessage = "3. Der letzte Schritt!";
                needsFinish = lastState == 1;
                break;
            case 0:
            default:
                infoMessage = "1. Einen der QR-Codes scannen";
                break;
        }

        helpText.setText(infoMessage);
        if (needsFinish) {
            Toast t = Toast.makeText(getApplicationContext(), "Der Austausch ist fertig!", Toast.LENGTH_LONG);
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
//                        FileHelper.writeUuid(getApplicationContext(), stringResults[1]);
                    } else {
                        // TODO Steffi: throw Error or something
                    }

                    // Wenn 3 Werte übermittelt wurden, dann muss Fingerprint vorhanden sein als letzter Eintrag
                    if (stringResults.length >= 3 && !stringResults[2].isEmpty()) {
                        String qrFingerprint = stringResults[2];
                        needsFinish = stringResults.length < 4;

                        checkFingerprint(mobileNumber, qrFingerprint);
                    }
//                    else {
//                        sendCheckMessage(mobileNumber);
//                    }
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

    private void sendCheckMessage(String mobileNumber) {
        Recipients recipients = RecipientFactory.getRecipientsFromString(this, mobileNumber, true);

        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
        intent.putExtra(ConversationActivity.TEXT_EXTRA, String.format("!@check_%s", "check"));
        intent.putExtra(ConversationActivity.IS_CHECK_EXTRA, true);
        intent.setDataAndType(getIntent().getData(), getIntent().getType());

        long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);

        intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
        intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
        startActivity(intent);
        finish();
    }

    private void checkFingerprint(String remoteNumber, final String qrFingerprint) {
        final Context context = getApplicationContext();
        // Steffi: remotenumber = Nummer des Empfängers ohne Leerzeichen!
        final String remNumber = remoteNumber.replace(" ", "");
        // Eigene Nummer
        final String localNumber = TextSecurePreferences.getLocalNumber(context);
        // Eigener IdentityKey
        final IdentityKey localIdentity = IdentityKeyUtil.getIdentityKey(context);
        // Empfänger als Recipient
        final Recipient recipient = RecipientFactory.getRecipientsFromString(context, remoteNumber, true).getPrimaryRecipient();
        MasterSecret masterSecret = KeyCachingService.getMasterSecret(getApplicationContext());

        // Utility Methode um IdentityKey des Empfängers zu ermitteln
        IdentityUtil.getRemoteIdentityKey(context, recipient).addListener(new ListenableFuture.Listener<Optional<IdentityDatabase.IdentityRecord>>() {
            @Override
            public void onSuccess(Optional<IdentityDatabase.IdentityRecord> result) {
                // Sobald IdentityKey des Empfängers ermittelt wurde
                if (result.isPresent()) {
                    // Generiere fingerprint
                    Fingerprint fingerprint = new NumericFingerprintGenerator(5200).createFor(localNumber, localIdentity,
                            remNumber, result.get().getIdentityKey());

                    if (fingerprint.getDisplayableFingerprint().getDisplayText().equals(qrFingerprint)) {
                        sendVCard(remNumber);
                    } else {
                        return;
                    }
                }
            }

            @Override
            public void onFailure(ExecutionException e) {
                e.printStackTrace();
                Log.e(TAG, "Error checking fingerprint");
            }
        });
    }

    private void sendVCard(String mobileNumber) {

        Context context = getApplicationContext();

        VCard vCard = VCard.getVCard(context);
        if (vCard != null) {
            try {
                String vCardString = JsonUtils.toJson(vCard);
                Recipients recipients = RecipientFactory.getRecipientsFromString(this, mobileNumber, true);

                Intent intent = new Intent(this, ConversationActivity.class);
                intent.putExtra(ConversationActivity.RECIPIENTS_EXTRA, recipients.getIds());
                intent.putExtra(ConversationActivity.TEXT_EXTRA, String.format("!@vcard_%s", vCardString));
                intent.putExtra(ConversationActivity.IS_VCARD_EXTRA, true);
                intent.putExtra(ConversationActivity.NEEDS_FINISH_EXTRA, needsFinish);
                intent.putExtra(ConversationActivity.LAST_SCAN_STATE_EXTRA, scanHelp);
                intent.setDataAndType(getIntent().getData(), getIntent().getType());

                long existingThread = DatabaseFactory.getThreadDatabase(this).getThreadIdIfExistsFor(recipients);

                intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, existingThread);
                intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
                startActivity(intent);
                finish();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
}
