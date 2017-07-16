package org.thoughtcrime.securesms;

import android.*;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.i18n.phonenumbers.AsYouTypeFormatter;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.thoughtcrime.securesms.additions.BlackList;
import org.thoughtcrime.securesms.additions.FileHelper;
import org.thoughtcrime.securesms.additions.ParentsContact;
import org.thoughtcrime.securesms.additions.VCard;
import org.thoughtcrime.securesms.additions.WhiteList;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.util.PhoneNumberFormatter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * The register account activity.  Prompts ths user for their registration information
 * and begins the account registration process.
 *
 * @author Moxie Marlinspike
 *
 */
public class RegistrationActivity extends BaseActionBarActivity {

  private static final int READ_EXTERNAL_STORAGE = 2;
  private static final int PICK_COUNTRY = 1;
  private static final String TAG = RegistrationActivity.class.getSimpleName();

  private enum PlayServicesStatus {
    SUCCESS,
    MISSING,
    NEEDS_UPDATE,
    TRANSIENT_ERROR
  }

  private AsYouTypeFormatter   countryFormatter;
  private ArrayAdapter<String> countrySpinnerAdapter;
  private Spinner              countrySpinner;
  private TextView             countryCode;
  private TextView             number;
  private TextView             createButton;
  private TextView             skipButton;
  private TextView             informationView;
  private View                 informationToggle;
  private TextView             informationToggleText;

  private MasterSecret masterSecret;

  // Steffi:
  // Zusätzliche Informationen, die für das System benötigt werden
  private TextView childFirstName;
  private TextView childLastName;
  private TextView parentsCountryCode;
  private TextView parentsNumber;
  private TextView parentFirstName;
  private TextView parentLastName;
  private TextView parentDisplayname;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.registration_activity);

    // Steffi: Zeile 87 - 92 => Abfrage der Berechtigung für externen Speicher, da einige Smartphones ihren Speicher als SD Karte simulieren
    if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

      ActivityCompat.requestPermissions(this,
              new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE);
    }
    getSupportActionBar().setTitle(getString(R.string.RegistrationActivity_connect_with_signal));

    initializeResources();
    initializeSpinner();
    initializeNumber();
  }

  // Steffi: Überprüfung, ob die Rechte gegeben wurden oder nicht.
  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    switch (requestCode) {
      // Steffi: Prüfung, ob Rechte für externen Speicher gewährt wurden oder nicht
      case READ_EXTERNAL_STORAGE:
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
          // Wenn gewährt, fahre fort
        } else {
          // wenn nicht gewährt, dann breche ab
          Toast.makeText(this, "Please grant external storage permission to use this app", Toast.LENGTH_SHORT).show();
          return;
        }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == PICK_COUNTRY && resultCode == RESULT_OK && data != null) {
      this.countryCode.setText(data.getIntExtra("country_code", 1)+"");
      setCountryDisplay(data.getStringExtra("country_name"));
      setCountryFormatter(data.getIntExtra("country_code", 1));
    }
  }

  private void initializeResources() {
    this.masterSecret   = getIntent().getParcelableExtra("master_secret");
    this.countrySpinner        = (Spinner) findViewById(R.id.country_spinner);
    this.countryCode           = (TextView) findViewById(R.id.country_code);
    this.number                = (TextView) findViewById(R.id.number);
    this.createButton          = (TextView) findViewById(R.id.registerButton);
    this.skipButton            = (TextView) findViewById(R.id.skipButton);
    this.informationView       = (TextView) findViewById(R.id.registration_information);
    this.informationToggle     =            findViewById(R.id.information_link_container);
    this.informationToggleText = (TextView) findViewById(R.id.information_label);

    this.createButton.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.signal_primary),
                                                     PorterDuff.Mode.MULTIPLY);
    this.skipButton.getBackground().setColorFilter(ContextCompat.getColor(this, R.color.grey_400),
                                                   PorterDuff.Mode.MULTIPLY);

    // Steffi:
    // Ermittlung der Werte aus der UI
    this.childFirstName = (TextView) findViewById(R.id.childFirstName);
    this.childLastName = (TextView) findViewById(R.id.childLastName);
    this.parentsCountryCode = (TextView) findViewById(R.id.parents_country_code);
    this.parentsNumber = (TextView) findViewById(R.id.parents_number);
    this.parentFirstName = (TextView) findViewById(R.id.parentFirstName);
    this.parentLastName = (TextView) findViewById(R.id.parentLastName);
    this.parentDisplayname = (TextView) findViewById(R.id.parentDisplayname);

    this.countryCode.addTextChangedListener(new CountryCodeChangedListener());
    this.number.addTextChangedListener(new NumberChangedListener());
    this.createButton.setOnClickListener(new CreateButtonListener());
    this.skipButton.setOnClickListener(new CancelButtonListener());
    this.informationToggle.setOnClickListener(new InformationToggleListener());

    // Steffi: Hinzufügen der ChangeListener
    this.parentsCountryCode.addTextChangedListener(new ParentsCountryCodeChangedListener());
    this.parentsNumber.addTextChangedListener(new NumberChangedListener());

    if (getIntent().getBooleanExtra("cancel_button", false)) {
      this.skipButton.setVisibility(View.VISIBLE);
    } else {
      this.skipButton.setVisibility(View.INVISIBLE);
    }
  }

  private void initializeSpinner() {
    this.countrySpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
    this.countrySpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));

    this.countrySpinner.setAdapter(this.countrySpinnerAdapter);
    this.countrySpinner.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
          Intent intent = new Intent(RegistrationActivity.this, CountrySelectionActivity.class);
          startActivityForResult(intent, PICK_COUNTRY);
        }
        return true;
      }
    });
    this.countrySpinner.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
          Intent intent = new Intent(RegistrationActivity.this, CountrySelectionActivity.class);
          startActivityForResult(intent, PICK_COUNTRY);
          return true;
        }
        return false;
      }
    });
  }

  private void initializeNumber() {
    PhoneNumberUtil numberUtil  = PhoneNumberUtil.getInstance();
    String          localNumber = Util.getDeviceE164Number(this);

    try {
      if (!TextUtils.isEmpty(localNumber)) {
        Phonenumber.PhoneNumber localNumberObject = numberUtil.parse(localNumber, null);

        if (localNumberObject != null) {
          this.countryCode.setText(String.valueOf(localNumberObject.getCountryCode()));
          this.number.setText(String.valueOf(localNumberObject.getNationalNumber()));
        }
      } else {
        String simCountryIso = Util.getSimCountryIso(this);

        if (!TextUtils.isEmpty(simCountryIso)) {
          this.parentsCountryCode.setText(numberUtil.getCountryCodeForRegion(simCountryIso)+"");
          this.countryCode.setText(numberUtil.getCountryCodeForRegion(simCountryIso)+"");
        }
      }
    } catch (NumberParseException npe) {
      Log.w(TAG, npe);
    }
  }

  private void setCountryDisplay(String value) {
    this.countrySpinnerAdapter.clear();
    this.countrySpinnerAdapter.add(value);
  }

  private void setCountryFormatter(int countryCode) {
    PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    String regionCode    = util.getRegionCodeForCountryCode(countryCode);

    if (regionCode == null) this.countryFormatter = null;
    else                    this.countryFormatter = util.getAsYouTypeFormatter(regionCode);
  }

  private String getConfiguredE164Number() {
    return PhoneNumberFormatter.formatE164(countryCode.getText().toString(),
                                           number.getText().toString());
  }

  private String getConfguredE164ParentNumber() {
    return PhoneNumberFormatter.formatE164(parentsCountryCode.getText().toString(),
            parentsNumber.getText().toString());
  }

  private class CreateButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      final RegistrationActivity self = RegistrationActivity.this;

      // Steffi: Einsprungspunkt, sobald auf "Registrieren"-Button gedrückt wird.
      // Überprüft, ob alle notwendigen Werte eingetragen wurden
      validateEntries(self);

      // Formatiert die angegebenen Nummern nach dem internationalen E.164 Nummer-Format
      final String e164number = getConfiguredE164Number();
      final String e164parentNumber = getConfguredE164ParentNumber();

      if (!PhoneNumberFormatter.isValidNumber(e164number)) {
        Dialogs.showAlertDialog(self,
                             getString(R.string.RegistrationActivity_invalid_number),
                             String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
                                           e164number));
        return;
      }

      if (!PhoneNumberFormatter.isValidNumber(e164parentNumber)) {
        Dialogs.showAlertDialog(self,
                getString(R.string.RegistrationActivity_invalid_number),
                String.format(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
                        e164parentNumber));
        return;
      }

      String cleanE164number = e164number.replaceAll(" ", "").trim().toLowerCase();
      String cleanParentE164number = e164parentNumber.replaceAll(" ", "").trim().toLowerCase();

      if (cleanE164number.equals(cleanParentE164number)) {
        Dialogs.showAlertDialog(self,
                getString(R.string.RegistrationActivity_invalid_number),
                String.format("Die Nummern müssen unterschiedlich sein",
                        e164parentNumber));
        return;
      }

      // Steffi: Erstellung der persönlichen VCard
      String pFirstName = parentFirstName.getText().toString();
      String pLastName = parentLastName.getText().toString();
      String pDisplayname = parentDisplayname.getText().toString();
      ParentsContact parentsContact = new ParentsContact(pFirstName, pLastName, e164parentNumber);
      VCard personalVCard = new VCard(childFirstName.getText().toString(), childLastName.getText().toString(), e164number);
      personalVCard.addParent(parentsContact);

      // Steffi: Erstellung der notwendigen Listen
      createFiles(self);
      // Mobilnummer des angegebenen Eltern-Kontaktes wird direkt der Whitelist hinzugefügt
      WhiteList.addNumberToFile(self, e164parentNumber, pDisplayname);
      createVCard(self, personalVCard);

      PlayServicesStatus gcmStatus = checkPlayServices(self);

      if (gcmStatus == PlayServicesStatus.SUCCESS) {
        promptForRegistrationStart(self, e164number, true);
      } else if (gcmStatus == PlayServicesStatus.MISSING) {
        promptForNoPlayServices(self, e164number);
      } else if (gcmStatus == PlayServicesStatus.NEEDS_UPDATE) {
        GoogleApiAvailability.getInstance().getErrorDialog(self, ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, 0).show();
      } else {
        Dialogs.showAlertDialog(self, getString(R.string.RegistrationActivity_play_services_error),
                                getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable));
      }
    }

    private void createFiles(final Context context) {
      try {
        createWhiteList(context);
        createBlackList(context);
        createPendingList(context);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private void createWhiteList(final Context context) throws IOException {
      WhiteList whiteListArray = new WhiteList();
      String whiteListString = JsonUtils.toJson(whiteListArray);
      FileHelper.writeDataToFile(context, whiteListString, FileHelper.whiteListFileName);
    }

    private void createBlackList(final Context context) throws IOException {
      BlackList blackList = new BlackList();
      String blackListString = JsonUtils.toJson(blackList);
      FileHelper.writeDataToFile(context, blackListString, FileHelper.blackListFileName);
    }

    private void createPendingList(final Context context) throws IOException {
      ArrayList<VCard> pendingListArray = new ArrayList<>();
      String pendingListString = JsonUtils.toJson(pendingListArray);
      FileHelper.writeDataToFile(context, pendingListString, FileHelper.pendingListFileName);
    }

    private void createVCard(RegistrationActivity context, VCard vCard) {
      String jsonChild = "";
      try {
        jsonChild = JsonUtils.toJson(vCard);
        FileHelper.writeDataToFile(context, jsonChild, FileHelper.vCardFileName);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private void validateEntries(final RegistrationActivity self) {
      if (TextUtils.isEmpty(countryCode.getText())) {
        Toast.makeText(self,
                getString(R.string.RegistrationActivity_you_must_specify_your_country_code),
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(parentsCountryCode.getText())) {
        Toast.makeText(self,
                "You must specify your country code",
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(number.getText())) {
        Toast.makeText(self,
                getString(R.string.RegistrationActivity_you_must_specify_your_phone_number),
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(childFirstName.getText())) {
        Toast.makeText(self,
                "You must enter the first name",
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(childLastName.getText())) {
        Toast.makeText(self,
                "You must enter the last name",
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(parentFirstName.getText())) {
        Toast.makeText(self,
                "You must enter the first name of parent",
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(parentLastName.getText())) {
        Toast.makeText(self,
                "You must enter the last name of parent",
                Toast.LENGTH_LONG).show();
        return;
      }

      if (TextUtils.isEmpty(parentsNumber.getText())) {
        Toast.makeText(self,
                getString(R.string.RegistrationActivity_you_must_specify_your_phone_number),
                Toast.LENGTH_LONG).show();
        return;
      }
    }

    private void promptForRegistrationStart(final Context context, final String e164number, final boolean gcmSupported) {
      AlertDialog.Builder dialog = new AlertDialog.Builder(context);
      dialog.setTitle(PhoneNumberFormatter.getInternationalFormatFromE164(e164number));
      dialog.setMessage(R.string.RegistrationActivity_we_will_now_verify_that_the_following_number_is_associated_with_your_device_s);
      dialog.setPositiveButton(getString(R.string.RegistrationActivity_continue),
                               new DialogInterface.OnClickListener() {
                                 @Override
                                 public void onClick(DialogInterface dialog, int which) {
                                   Intent intent = new Intent(context, RegistrationProgressActivity.class);
                                   intent.putExtra(RegistrationProgressActivity.NUMBER_EXTRA, e164number);
                                   intent.putExtra(RegistrationProgressActivity.MASTER_SECRET_EXTRA, masterSecret);
                                   intent.putExtra(RegistrationProgressActivity.GCM_SUPPORTED_EXTRA, gcmSupported);
                                   startActivity(intent);
                                   finish();
                                 }
                               });
      dialog.setNegativeButton(getString(R.string.RegistrationActivity_edit), null);
      dialog.show();
    }

    private void promptForNoPlayServices(final Context context, final String e164number) {
      AlertDialog.Builder dialog = new AlertDialog.Builder(context);
      dialog.setTitle(R.string.RegistrationActivity_missing_google_play_services);
      dialog.setMessage(R.string.RegistrationActivity_this_device_is_missing_google_play_services);
      dialog.setPositiveButton(R.string.RegistrationActivity_i_understand, new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
          promptForRegistrationStart(context, e164number, false);
        }
      });
      dialog.setNegativeButton(android.R.string.cancel, null);
      dialog.show();
    }

    private PlayServicesStatus checkPlayServices(Context context) {
      int gcmStatus = 0;

      try {
        gcmStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
      } catch (Throwable t) {
        Log.w(TAG, t);
        return PlayServicesStatus.MISSING;
      }

      Log.w(TAG, "Play Services: " + gcmStatus);

      switch (gcmStatus) {
        case ConnectionResult.SUCCESS:
          return PlayServicesStatus.SUCCESS;
        case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
          try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo("com.google.android.gms", 0);

            if (applicationInfo != null && !applicationInfo.enabled) {
              return PlayServicesStatus.MISSING;
            }
          } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e);
          }

          return PlayServicesStatus.NEEDS_UPDATE;
        case ConnectionResult.SERVICE_DISABLED:
        case ConnectionResult.SERVICE_MISSING:
        case ConnectionResult.SERVICE_INVALID:
        case ConnectionResult.API_UNAVAILABLE:
        case ConnectionResult.SERVICE_MISSING_PERMISSION:
          return PlayServicesStatus.MISSING;
        default:
          return PlayServicesStatus.TRANSIENT_ERROR;
      }
    }
  }

  private class CountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (TextUtils.isEmpty(s)) {
        setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));
        countryFormatter = null;
        return;
      }

      int countryCode   = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);

      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!TextUtils.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        number.requestFocus();
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  private class ParentsCountryCodeChangedListener implements TextWatcher {
    @Override
    public void afterTextChanged(Editable s) {
      if (TextUtils.isEmpty(s)) {
        setCountryDisplay(getString(R.string.RegistrationActivity_select_your_country));
        countryFormatter = null;
        return;
      }

      int countryCode = Integer.parseInt(s.toString());
      String regionCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(countryCode);

      setCountryFormatter(countryCode);
      setCountryDisplay(PhoneNumberFormatter.getRegionDisplayName(regionCode));

      if (!TextUtils.isEmpty(regionCode) && !regionCode.equals("ZZ")) {
        parentsNumber.requestFocus();
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }
  }

  private class NumberChangedListener implements TextWatcher {

    @Override
    public void afterTextChanged(Editable s) {
      if (countryFormatter == null)
        return;

      if (TextUtils.isEmpty(s))
        return;

      countryFormatter.clear();

      String number          = s.toString().replaceAll("[^\\d.]", "");
      String formattedNumber = null;

      for (int i=0;i<number.length();i++) {
        formattedNumber = countryFormatter.inputDigit(number.charAt(i));
      }

      if (formattedNumber != null && !s.toString().equals(formattedNumber)) {
        s.replace(0, s.length(), formattedNumber);
      }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }
  }

  private class CancelButtonListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      TextSecurePreferences.setPromptedPushRegistration(RegistrationActivity.this, true);
      Intent nextIntent = getIntent().getParcelableExtra("next_intent");

      if (nextIntent == null) {
        nextIntent = new Intent(RegistrationActivity.this, ConversationListActivity.class);
      }

      startActivity(nextIntent);
      finish();
    }
  }

  private class InformationToggleListener implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      if (informationView.getVisibility() == View.VISIBLE) {
        informationView.setVisibility(View.GONE);
        informationToggleText.setText(R.string.RegistrationActivity_more_information);
      } else {
        informationView.setVisibility(View.VISIBLE);
        informationToggleText.setText(R.string.RegistrationActivity_less_information);
      }
    }
  }
}
