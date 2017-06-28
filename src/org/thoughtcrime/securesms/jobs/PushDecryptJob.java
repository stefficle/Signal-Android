package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.additions.BlackList;
import org.thoughtcrime.securesms.additions.FileHelper;
import org.thoughtcrime.securesms.additions.MessageHelper;
import org.thoughtcrime.securesms.additions.ParentsContact;
import org.thoughtcrime.securesms.additions.PendingList;
import org.thoughtcrime.securesms.additions.VCard;
import org.thoughtcrime.securesms.additions.WhiteList;
import org.thoughtcrime.securesms.attachments.DatabaseAttachment;
import org.thoughtcrime.securesms.attachments.PointerAttachment;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.crypto.MasterSecretUnion;
import org.thoughtcrime.securesms.crypto.MasterSecretUtil;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.thoughtcrime.securesms.crypto.storage.TextSecureSessionStore;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.MessagingDatabase.InsertResult;
import org.thoughtcrime.securesms.database.MessagingDatabase.SyncMessageId;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.NotInDirectoryException;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.groups.GroupMessageProcessor;
import org.thoughtcrime.securesms.mms.IncomingMediaMessage;
import org.thoughtcrime.securesms.mms.MmsException;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.service.WebRtcCallService;
import org.thoughtcrime.securesms.sms.IncomingEncryptedMessage;
import org.thoughtcrime.securesms.sms.IncomingEndSessionMessage;
import org.thoughtcrime.securesms.sms.IncomingPreKeyBundleMessage;
import org.thoughtcrime.securesms.sms.IncomingTextMessage;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.DirectoryHelper;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.jobqueue.JobParameters;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.messages.SignalServiceGroup;
import org.whispersystems.signalservice.api.messages.calls.AnswerMessage;
import org.whispersystems.signalservice.api.messages.calls.BusyMessage;
import org.whispersystems.signalservice.api.messages.calls.HangupMessage;
import org.whispersystems.signalservice.api.messages.calls.IceUpdateMessage;
import org.whispersystems.signalservice.api.messages.calls.OfferMessage;
import org.whispersystems.signalservice.api.messages.calls.SignalServiceCallMessage;
import org.whispersystems.signalservice.api.messages.multidevice.ReadMessage;
import org.whispersystems.signalservice.api.messages.multidevice.RequestMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SentTranscriptMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.messages.multidevice.VerifiedMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PushDecryptJob extends ContextJob {

  private static final long serialVersionUID = 2L;

  public static final String TAG = PushDecryptJob.class.getSimpleName();

  private final long messageId;
  private final long smsMessageId;

  public PushDecryptJob(Context context, long pushMessageId, String sender) {
    this(context, pushMessageId, -1, sender);
  }

  public PushDecryptJob(Context context, long pushMessageId, long smsMessageId, String sender) {
    super(context, JobParameters.newBuilder()
                                .withPersistence()
                                .withGroupId("__PUSH_DECRYPT_JOB__")
                                .withWakeLock(true, 5, TimeUnit.SECONDS)
                                .create());
    this.messageId    = pushMessageId;
    this.smsMessageId = smsMessageId;
  }

  @Override
  public void onAdded() {}

  @Override
  public void onRun() throws NoSuchMessageException {

    if (!IdentityKeyUtil.hasIdentityKey(context)) {
      Log.w(TAG, "Skipping job, waiting for migration...");
      return;
    }

    MasterSecret          masterSecret         = KeyCachingService.getMasterSecret(context);
    PushDatabase          database             = DatabaseFactory.getPushDatabase(context);
    SignalServiceEnvelope envelope             = database.get(messageId);
    Optional<Long>        optionalSmsMessageId = smsMessageId > 0 ? Optional.of(smsMessageId) :
                                                                 Optional.<Long>absent();

    MasterSecretUnion masterSecretUnion;

    if (masterSecret == null) masterSecretUnion = new MasterSecretUnion(MasterSecretUtil.getAsymmetricMasterSecret(context, null));
    else                      masterSecretUnion = new MasterSecretUnion(masterSecret);

    handleMessage(masterSecretUnion, envelope, optionalSmsMessageId);
    database.delete(messageId);
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return false;
  }

  @Override
  public void onCanceled() {

  }

  private void handleMessage(MasterSecretUnion masterSecret, SignalServiceEnvelope envelope, Optional<Long> smsMessageId) {
    try {
      GroupDatabase        groupDatabase = DatabaseFactory.getGroupDatabase(context);
      SignalProtocolStore  axolotlStore  = new SignalProtocolStoreImpl(context);
      SignalServiceAddress localAddress  = new SignalServiceAddress(TextSecurePreferences.getLocalNumber(context));
      SignalServiceCipher  cipher        = new SignalServiceCipher(localAddress, axolotlStore);

      SignalServiceContent content = cipher.decrypt(envelope);

      if (content.getDataMessage().isPresent()) {
        SignalServiceDataMessage message = content.getDataMessage().get();

        if      (message.isEndSession())               handleEndSessionMessage(masterSecret, envelope, message, smsMessageId);
        else if (message.isGroupUpdate())
          return; //handleGroupMessage(masterSecret, envelope, message, smsMessageId); // Steffi: Gruppen-Daten-Nachricht deaktivieren
        else if (message.isExpirationUpdate())
          return; // TODO: wieso? handleExpirationUpdate(masterSecret, envelope, message, smsMessageId);
        else if (message.getAttachments().isPresent()) handleMediaMessage(masterSecret, envelope, message, smsMessageId);
        else                                           handleTextMessage(masterSecret, envelope, message, smsMessageId);

        if (message.getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(message.getGroupInfo().get().getGroupId())) {
          return;
          // Steffi: Unbekannte Gruppe wird nicht mehr bearbeitet
          // handleUnknownGroupMessage(envelope, message.getGroupInfo().get());
        }
      } else if (content.getSyncMessage().isPresent()) {
        SignalServiceSyncMessage syncMessage = content.getSyncMessage().get();

        if      (syncMessage.getSent().isPresent())     handleSynchronizeSentMessage(masterSecret, envelope, syncMessage.getSent().get(), smsMessageId);
        else if (syncMessage.getRequest().isPresent())  handleSynchronizeRequestMessage(masterSecret, syncMessage.getRequest().get());
        else if (syncMessage.getRead().isPresent())     handleSynchronizeReadMessage(masterSecret, syncMessage.getRead().get(), envelope.getTimestamp());
        else if (syncMessage.getVerified().isPresent()) handleSynchronizeVerifiedMessage(masterSecret, syncMessage.getVerified().get());
        else                                           Log.w(TAG, "Contains no known sync types...");
      } else
      // Steffi: Call deaktiviert
//        if (content.getCallMessage().isPresent()) {
//        Log.w(TAG, "Got call message...");
//        SignalServiceCallMessage message = content.getCallMessage().get();
//
//        if      (message.getOfferMessage().isPresent())      handleCallOfferMessage(envelope, message.getOfferMessage().get(), smsMessageId);
//        else if (message.getAnswerMessage().isPresent())     handleCallAnswerMessage(envelope, message.getAnswerMessage().get());
//        else if (message.getIceUpdateMessages().isPresent()) handleCallIceUpdateMessage(envelope, message.getIceUpdateMessages().get());
//        else if (message.getHangupMessage().isPresent())     handleCallHangupMessage(envelope, message.getHangupMessage().get(), smsMessageId);
//        else if (message.getBusyMessage().isPresent())       handleCallBusyMessage(envelope, message.getBusyMessage().get());
//      } else
      {
        Log.w(TAG, "Got unrecognized message...");
      }

      if (envelope.isPreKeySignalMessage()) {
        ApplicationContext.getInstance(context).getJobManager().add(new RefreshPreKeysJob(context));
      }
    } catch (InvalidVersionException e) {
      Log.w(TAG, e);
      handleInvalidVersionMessage(masterSecret, envelope, smsMessageId);
    } catch (InvalidMessageException | InvalidKeyIdException | InvalidKeyException | MmsException e) {
      Log.w(TAG, e);
      handleCorruptMessage(masterSecret, envelope, smsMessageId);
    } catch (NoSessionException e) {
      Log.w(TAG, e);
      handleNoSessionMessage(masterSecret, envelope, smsMessageId);
    } catch (LegacyMessageException e) {
      Log.w(TAG, e);
      handleLegacyMessage(masterSecret, envelope, smsMessageId);
    } catch (DuplicateMessageException e) {
      Log.w(TAG, e);
      handleDuplicateMessage(masterSecret, envelope, smsMessageId);
    } catch (UntrustedIdentityException e) {
      Log.w(TAG, e);
      handleUntrustedIdentityMessage(masterSecret, envelope, smsMessageId);
    }
  }

  private void handleCallOfferMessage(@NonNull SignalServiceEnvelope envelope,
                                      @NonNull OfferMessage message,
                                      @NonNull Optional<Long> smsMessageId)
  {
    Log.w(TAG, "handleCallOfferMessage...");

    if (smsMessageId.isPresent()) {
      SmsDatabase database = DatabaseFactory.getSmsDatabase(context);
      database.markAsMissedCall(smsMessageId.get());
    } else {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_INCOMING_CALL);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_NUMBER, envelope.getSource());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());
      intent.putExtra(WebRtcCallService.EXTRA_TIMESTAMP, envelope.getTimestamp());
      context.startService(intent);
    }
  }

  private void handleCallAnswerMessage(@NonNull SignalServiceEnvelope envelope,
                                       @NonNull AnswerMessage message)
  {
    Log.w(TAG, "handleCallAnswerMessage...");
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_RESPONSE_MESSAGE);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_NUMBER, envelope.getSource());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_DESCRIPTION, message.getDescription());
    context.startService(intent);
  }

  private void handleCallIceUpdateMessage(@NonNull SignalServiceEnvelope envelope,
                                          @NonNull List<IceUpdateMessage> messages)
  {
    Log.w(TAG, "handleCallIceUpdateMessage... " + messages.size());
    for (IceUpdateMessage message : messages) {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_ICE_MESSAGE);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_NUMBER, envelope.getSource());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP, message.getSdp());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_MID, message.getSdpMid());
      intent.putExtra(WebRtcCallService.EXTRA_ICE_SDP_LINE_INDEX, message.getSdpMLineIndex());
      context.startService(intent);
    }
  }

  private void handleCallHangupMessage(@NonNull SignalServiceEnvelope envelope,
                                       @NonNull HangupMessage message,
                                       @NonNull Optional<Long> smsMessageId)
  {
    Log.w(TAG, "handleCallHangupMessage");
    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).markAsMissedCall(smsMessageId.get());
    } else {
      Intent intent = new Intent(context, WebRtcCallService.class);
      intent.setAction(WebRtcCallService.ACTION_REMOTE_HANGUP);
      intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
      intent.putExtra(WebRtcCallService.EXTRA_REMOTE_NUMBER, envelope.getSource());
      context.startService(intent);
    }
  }

  private void handleCallBusyMessage(@NonNull SignalServiceEnvelope envelope,
                                     @NonNull BusyMessage message)
  {
    Intent intent = new Intent(context, WebRtcCallService.class);
    intent.setAction(WebRtcCallService.ACTION_REMOTE_BUSY);
    intent.putExtra(WebRtcCallService.EXTRA_CALL_ID, message.getId());
    intent.putExtra(WebRtcCallService.EXTRA_REMOTE_NUMBER, envelope.getSource());
    context.startService(intent);
  }

  private void handleEndSessionMessage(@NonNull MasterSecretUnion        masterSecret,
                                       @NonNull SignalServiceEnvelope    envelope,
                                       @NonNull SignalServiceDataMessage message,
                                       @NonNull Optional<Long>           smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase         = DatabaseFactory.getEncryptingSmsDatabase(context);
    IncomingTextMessage   incomingTextMessage = new IncomingTextMessage(envelope.getSource(),
                                                                        envelope.getSourceDevice(),
                                                                        message.getTimestamp(),
                                                                        "", Optional.<SignalServiceGroup>absent(), 0);

    Long threadId;

    if (!smsMessageId.isPresent()) {
      IncomingEndSessionMessage incomingEndSessionMessage = new IncomingEndSessionMessage(incomingTextMessage);
      Optional<InsertResult>    insertResult              = smsDatabase.insertMessageInbox(masterSecret, incomingEndSessionMessage);

      if (insertResult.isPresent()) threadId = insertResult.get().getThreadId();
      else                          threadId = null;
    } else {
      smsDatabase.markAsEndSession(smsMessageId.get());
      threadId = smsDatabase.getThreadIdForMessage(smsMessageId.get());
    }

    if (threadId != null) {
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(envelope.getSource());

      SecurityEvent.broadcastSecurityUpdateEvent(context);
      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), threadId);
    }
  }

  private long handleSynchronizeSentEndSessionMessage(@NonNull MasterSecretUnion     masterSecret,
                                                      @NonNull SentTranscriptMessage message,
                                                      @NonNull Optional<Long>        smsMessageId)
  {
    EncryptingSmsDatabase     database                  = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients                recipients                = getSyncMessageDestination(message);
    OutgoingTextMessage       outgoingTextMessage       = new OutgoingTextMessage(recipients, "", -1);
    OutgoingEndSessionMessage outgoingEndSessionMessage = new OutgoingEndSessionMessage(outgoingTextMessage);

    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);

    if (recipients.isSingleRecipient() && !recipients.isGroupRecipient()) {
      SessionStore sessionStore = new TextSecureSessionStore(context);
      sessionStore.deleteAllSessions(recipients.getPrimaryRecipient().getNumber());

      SecurityEvent.broadcastSecurityUpdateEvent(context);

      long messageId = database.insertMessageOutbox(masterSecret, threadId, outgoingEndSessionMessage,
                                                    false, message.getTimestamp(), null);
      database.markAsSent(messageId, true);
    }

    if (smsMessageId.isPresent()) {
      database.deleteMessage(smsMessageId.get());
    }

    return threadId;
  }

  private void handleGroupMessage(@NonNull MasterSecretUnion masterSecret,
                                  @NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
  {
    GroupMessageProcessor.process(context, masterSecret, envelope, message, false);

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleUnknownGroupMessage(@NonNull SignalServiceEnvelope envelope,
                                         @NonNull SignalServiceGroup group)
  {
    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new RequestGroupInfoJob(context, envelope.getSource(), group.getGroupId()));
  }

  private void handleExpirationUpdate(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceEnvelope envelope,
                                      @NonNull SignalServiceDataMessage message,
                                      @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    String               localNumber  = TextSecurePreferences.getLocalNumber(context);
    Recipients           recipients   = getMessageDestination(envelope, message);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, envelope.getSource(),
                                                                 localNumber, message.getTimestamp(), -1,
                                                                 message.getExpiresInSeconds() * 1000, true,
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 Optional.<String>absent(), message.getGroupInfo(),
                                                                 Optional.<List<SignalServiceAttachment>>absent());



    database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, -1);

    DatabaseFactory.getRecipientPreferenceDatabase(context).setExpireMessages(recipients, message.getExpiresInSeconds());

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }
  }

  private void handleSynchronizeVerifiedMessage(@NonNull MasterSecretUnion masterSecret,
                                                @NonNull List<VerifiedMessage> verifiedMessages)
  {
//    for (VerifiedMessage verifiedMessage : verifiedMessages) {
//      IdentityUtil.processVerifiedMessage(context, masterSecret, verifiedMessage);
//    }
  }

  private void handleSynchronizeSentMessage(@NonNull MasterSecretUnion masterSecret,
                                            @NonNull SignalServiceEnvelope envelope,
                                            @NonNull SentTranscriptMessage message,
                                            @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

    Long threadId;

    if (message.getMessage().isEndSession()) {
      threadId = handleSynchronizeSentEndSessionMessage(masterSecret, message, smsMessageId);
    } else if (message.getMessage().isGroupUpdate()) {
      threadId = GroupMessageProcessor.process(context, masterSecret, envelope, message.getMessage(), true);
    } else if (message.getMessage().isExpirationUpdate()) {
      threadId = handleSynchronizeSentExpirationUpdate(masterSecret, message, smsMessageId);
    } else if (message.getMessage().getAttachments().isPresent()) {
      threadId = handleSynchronizeSentMediaMessage(masterSecret, message, smsMessageId);
    } else {
      threadId = handleSynchronizeSentTextMessage(masterSecret, message, smsMessageId);
    }

    if (message.getMessage().getGroupInfo().isPresent() && groupDatabase.isUnknownGroup(message.getMessage().getGroupInfo().get().getGroupId())) {
      handleUnknownGroupMessage(envelope, message.getMessage().getGroupInfo().get());
    }

    if (threadId != null) {
      DatabaseFactory.getThreadDatabase(getContext()).setRead(threadId, true);
      MessageNotifier.updateNotification(getContext(), masterSecret.getMasterSecret().orNull());
    }

    MessageNotifier.setLastDesktopActivityTimestamp(message.getTimestamp());
  }

  private void handleSynchronizeRequestMessage(@NonNull MasterSecretUnion masterSecret,
                                               @NonNull RequestMessage message)
  {
    if (message.isContactsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceContactUpdateJob(getContext()));

      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceVerifiedUpdateJob(getContext()));
    }

    if (message.isGroupsRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceGroupUpdateJob(getContext()));
    }

    if (message.isBlockedListRequest()) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new MultiDeviceBlockedUpdateJob(getContext()));
    }
  }

  private void handleSynchronizeReadMessage(@NonNull MasterSecretUnion masterSecret,
                                            @NonNull List<ReadMessage> readMessages,
                                            long envelopeTimestamp)
  {
    for (ReadMessage readMessage : readMessages) {
      List<Pair<Long, Long>> expiringText = DatabaseFactory.getSmsDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()), envelopeTimestamp);
      List<Pair<Long, Long>> expiringMedia = DatabaseFactory.getMmsDatabase(context).setTimestampRead(new SyncMessageId(readMessage.getSender(), readMessage.getTimestamp()), envelopeTimestamp);

      for (Pair<Long, Long> expiringMessage : expiringText) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first, false, envelopeTimestamp, expiringMessage.second);
      }

      for (Pair<Long, Long> expiringMessage : expiringMedia) {
        ApplicationContext.getInstance(context)
                          .getExpiringMessageManager()
                          .scheduleDeletion(expiringMessage.first, true, envelopeTimestamp, expiringMessage.second);
      }
    }

    MessageNotifier.setLastDesktopActivityTimestamp(envelopeTimestamp);
    MessageNotifier.cancelDelayedNotifications();
    MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull());
  }

  private void handleMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                  @NonNull SignalServiceEnvelope envelope,
                                  @NonNull SignalServiceDataMessage message,
                                  @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase          database     = DatabaseFactory.getMmsDatabase(context);
    String               localNumber  = TextSecurePreferences.getLocalNumber(context);
    Recipients           recipients   = getMessageDestination(envelope, message);
    IncomingMediaMessage mediaMessage = new IncomingMediaMessage(masterSecret, envelope.getSource(),
                                                                 localNumber, message.getTimestamp(), -1,
                                                                 message.getExpiresInSeconds() * 1000, false,
                                                                 Optional.fromNullable(envelope.getRelay()),
                                                                 message.getBody(),
                                                                 message.getGroupInfo(),
                                                                message.getAttachments());
    // TODO: Beschreibung
    String source = envelope.getSource();
    if (!isInWhiteList(source)) return;

    if (message.getExpiresInSeconds() != recipients.getExpireMessages()) {
      handleExpirationUpdate(masterSecret, envelope, message, Optional.<Long>absent());
    }

    Optional<InsertResult> insertResult = database.insertSecureDecryptedMessageInbox(masterSecret, mediaMessage, -1);

    if (insertResult.isPresent()) {
      List<DatabaseAttachment> attachments = DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(null, insertResult.get().getMessageId());

      for (DatabaseAttachment attachment : attachments) {
        ApplicationContext.getInstance(context)
                          .getJobManager()
                          .add(new AttachmentDownloadJob(context, insertResult.get().getMessageId(),
                                                         attachment.getAttachmentId()));

        if (!masterSecret.getMasterSecret().isPresent()) {
          ApplicationContext.getInstance(context)
                            .getJobManager()
                            .add(new AttachmentFileNameJob(context, masterSecret.getAsymmetricMasterSecret().get(), attachment, mediaMessage));
        }
      }

      if (smsMessageId.isPresent()) {
        DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
      }

      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), insertResult.get().getThreadId());
    }
  }

  private long handleSynchronizeSentExpirationUpdate(@NonNull MasterSecretUnion masterSecret,
                                                     @NonNull SentTranscriptMessage message,
                                                     @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase database   = DatabaseFactory.getMmsDatabase(context);
    Recipients  recipients = getSyncMessageDestination(message);

    OutgoingExpirationUpdateMessage expirationUpdateMessage = new OutgoingExpirationUpdateMessage(recipients,
                                                                                                  message.getTimestamp(),
                                                                                                  message.getMessage().getExpiresInSeconds() * 1000);

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, expirationUpdateMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    DatabaseFactory.getRecipientPreferenceDatabase(context).setExpireMessages(recipients, message.getMessage().getExpiresInSeconds());

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }

    return threadId;
  }

  private long handleSynchronizeSentMediaMessage(@NonNull MasterSecretUnion masterSecret,
                                                 @NonNull SentTranscriptMessage message,
                                                 @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    MmsDatabase           database     = DatabaseFactory.getMmsDatabase(context);
    Recipients            recipients   = getSyncMessageDestination(message);
    OutgoingMediaMessage  mediaMessage = new OutgoingMediaMessage(recipients, message.getMessage().getBody().orNull(),
                                                                  PointerAttachment.forPointers(masterSecret, message.getMessage().getAttachments()),
                                                                  message.getTimestamp(), -1,
                                                                  message.getMessage().getExpiresInSeconds() * 1000,
                                                                  ThreadDatabase.DistributionTypes.DEFAULT);

    mediaMessage = new OutgoingSecureMediaMessage(mediaMessage);

    if (recipients.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(masterSecret, message, Optional.<Long>absent());
    }

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, mediaMessage, threadId, false, null);

    database.markAsSent(messageId, true);

    for (DatabaseAttachment attachment : DatabaseFactory.getAttachmentDatabase(context).getAttachmentsForMessage(null, messageId)) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new AttachmentDownloadJob(context, messageId, attachment.getAttachmentId()));
    }

    if (smsMessageId.isPresent()) {
      DatabaseFactory.getSmsDatabase(context).deleteMessage(smsMessageId.get());
    }

    if (message.getMessage().getExpiresInSeconds() > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationContext.getInstance(context)
                        .getExpiringMessageManager()
                        .scheduleDeletion(messageId, true,
                                          message.getExpirationStartTimestamp(),
                                          message.getMessage().getExpiresInSeconds());
    }

    return threadId;
  }

  private boolean isInWhiteList(String source) {
    WhiteList whiteList = WhiteList.getWhiteListContent(context);
    return whiteList.isInWhiteList(source);
  }

  private boolean isFromParents(String source) {
    VCard personalVCard = getPersonalVCard();
    boolean isFromParents = false;

    for (ParentsContact p : personalVCard.getParents()) {
      if (p.getMobileNumber().equals(source)) {
        isFromParents = true;
        break;
      }
    }

    return isFromParents;
  }

  private VCard getPersonalVCard() {
    VCard personalVCard = new VCard();
    String jsonString = FileHelper.readDataFromFile(context, FileHelper.vCardFileName);
    try {
      personalVCard = JsonUtils.fromJson(jsonString, VCard.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return personalVCard;
  }

  private boolean isSpecialMessage(String message) {
    return MessageHelper.startsWithSpecialCode(message);
  }

  // Blocken eines Kontakts
  private void blockContact(String message) {
    int id = MessageHelper.getIdFromMessage(message);
    String number = MessageHelper.getNumberFromMessageForRemoval(message);

    Date expDate = BlackList.getExpirationDate();
    if (MessageHelper.isPermanentBlock(message)) {
      expDate = null;
    }
    if (id > 0) {
      VCard vCard = null;
      try {
        vCard = PendingList.removeVCardById(context, id);
        if (vCard != null) {
          BlackList.addNumberToFile(context, vCard.getMobileNumber(), expDate);
        }
      } catch (Exception e) {
        e.printStackTrace();
        if (vCard != null) {
          PendingList.addNewVCard(context, vCard);
        }
      }
    } else if (!number.isEmpty()) {
      try {
        BlackList.addNumberToFile(context, number, expDate);
        WhiteList.removeNumberFromFile(context, number);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }

  // Kontakt erlauben
  private void approveContact(String message) {
    Integer id = MessageHelper.getIdFromMessage(message);
    if (id > 0) {
      VCard vCard = null;
      try {
        vCard = PendingList.removeVCardById(context, id);
        if (vCard != null) {
          WhiteList.addNumberToFile(context, vCard.getMobileNumber(), vCard.getFirstName() + " " + vCard.getLastName());

          MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
          DirectoryHelper.refreshDirectory(context, masterSecret);
        }
      } catch (Exception e) {
        e.printStackTrace();
        if (vCard != null) {
          PendingList.addNewVCard(context, vCard);
        }
      }
    }
  }

  // Neuen Kontakt hinzufügen
  private void addNewContact(String message) {
    String number = MessageHelper.getNumberFromMessage(message);
    String displayName = MessageHelper.getDisplayNameFromMessage(message);
    WhiteList.addNumberToFile(context, number, displayName);

    MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
    try {
      DirectoryHelper.refreshDirectory(context, masterSecret);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // Angefragte Liste anfordern
  private void listContent(String message, String source, Recipients recipients) {
    String listName = MessageHelper.getListNameFromMessage(message);
    String messageText = "";
    // Steffi: Methode, um angefragte Liste zurück zu liefern
    try {
      switch (listName) {
        case "geblockt":
          BlackList.checkExpirationDates(context);
          messageText += "Folgende Einträge befinden sich in der blockierten Liste:\n";
          BlackList blackList = BlackList.getBlackListContent(context);
          HashMap<String, Date> contacts = blackList.getBlockedContacts();
          for (Map.Entry<String, Date> c : contacts.entrySet()) {
            messageText += String.format("\nNummer: %1$s , Läuft ab: %2$s\n", c.getKey(), c.getValue() != null ? c.getValue() : "Nie");
          }
          break;
        case "erlaubt":
          messageText += "Folgende Einträge befinden sich in der erlaubten Liste:\n";
          WhiteList whiteList = WhiteList.getWhiteListContent(context);
          HashMap<String, String> whiteContacts = whiteList.getContactList();
          for (Map.Entry<String, String> c : whiteContacts.entrySet()) {
            messageText += String.format("\nNummer: %1$s , Name: %2$s\n", c.getKey(), c.getValue());
          }
          break;
        case "wartet":
          PendingList.checkExpirationDates(context);
          messageText += "Folgende Einträge sind auf der Warteliste:\n";
          PendingList pendingList = PendingList.getPendingListContent(context);
          HashMap<Integer, VCard> pendingContacts = pendingList.getPendingVCards();
          for (Map.Entry<Integer, VCard> c : pendingContacts.entrySet()) {
            messageText += String.format("\nID: %1$d - Nummer: %2$s , Name: %3$s\n",
                    c.getKey(),
                    c.getValue().getMobileNumber(),
                    c.getValue().getFirstName() + " " + c.getValue().getLastName());
          }
          break;
        default:
          messageText = String.format("Liste mit dem Namen %1$s ist nicht verfügbar", listName);
          break;
      }
      if (!message.isEmpty()) {
        SendMessage(messageText, source, recipients);
      } else {
        return;
      }
    } catch (NotInDirectoryException nide) {
      nide.printStackTrace();
    }
  }

  private void sendHelpMessage(String source, Recipients recipients) {
    try {
      String helpMessageText = "Sie können folgende Kommandos nutzen:\n\n" +
              "!@ ok ID - Kontakt bestätigen" +
              "\n\n!@ block ID - Kontakt für 2 Wochen blockieren" +
              "\n\n!@ block ID perm - Kontakt permanent blockieren" +
              "\n\n!@ new NUMMER ANZEIGENAME - neue Nummer mit Anzeigenamen hinzufügen" +
              "\n\n!@ list LISTENNAME - zeigt gewünschte Liste an; zur Verfügung stehende Listen: geblockt, erlaubt, wartet" +
              "\n\n!@ remove NUMMER - entfernt jeden Eintrag mit der entsprechenden Nummer";
      SendMessage(helpMessageText, source, recipients);
    } catch (NotInDirectoryException nide) {
      nide.printStackTrace();
    }
  }

  /**
   * Hilfsmethode zum Versenden einer Text-Nachricht als Antwort auf ein Spezial-Kommando
   *
   * @param messageText
   * @param recipients
   * @throws NotInDirectoryException
   */
  private void SendMessage(String messageText, String source, Recipients recipients) throws NotInDirectoryException {
    final MasterSecret masterSecret = KeyCachingService.getMasterSecret(context);
    boolean isSecureText = TextSecureDirectory.getInstance(context).isSecureTextSupported(source);

    OutgoingTextMessage message = null;

    // Steffi: subscriptionId ermitteln
    long expiresIn = -1;
    int subscriptionId = 0;

    if (isSecureText) {
      message = new OutgoingEncryptedMessage(recipients, messageText, expiresIn);
    } else {
      message = new OutgoingTextMessage(recipients, messageText, expiresIn, subscriptionId);
    }


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

      }
    }.execute(message);
  }

//  private void validateSpecialMessage(String message, String source, Recipients recipients) {
//    if (MessageHelper.isDebugCommand(message)) {
//      HandleDebugCommand(message, source, recipients);
//    } else {
//      HandleCommand(message, source, recipients);
//    }
//  }

//  private void HandleDebugCommand(String message, String source, Recipients recipients) {
//    String code = MessageHelper.getDebugCommand(message);
//
//    switch (code) {
//      case "warte":
//        addToPendingList(message);
//        break;
//      default:
//        break;
//    }
//  }

//  private void addToPendingList(String message) {
//    String mobileNumber = MessageHelper.getNumberForDebug(message);
//    String firstName = MessageHelper.getFirstNameForDebug(message);
//    String lastName = MessageHelper.getLastNameForDebug(message);
//    VCard newVCard = new VCard(firstName, lastName, mobileNumber);
//    PendingList.addNewVCard(context, newVCard);
//  }

  private void HandleCommand(String message, String source, Recipients recipients) {
    String code = MessageHelper.getCommandFromMessage(message);

    switch (code) {
      case "ok":
        approveContact(message);
        break;
      case "block":
        blockContact(message);
        break;
      case "new":
        addNewContact(message);
        break;
      case "list":
        listContent(message, source, recipients);
        break;
      case "help":
        sendHelpMessage(source, recipients);
        break;
      case "remove":
        removeByNumber(message);
      default:
        break;
    }
  }

  private void removeByNumber(String message) {
    String number = MessageHelper.getNumberFromMessageForRemoval(message);
    WhiteList.removeNumberFromFile(context, number);
    BlackList.removeNumberFromFile(context, number);
    PendingList.removeVCardByNumber(context, number);
  }

  // Steffi: Einsprungspunkt bei Erhalt einer Nachricht
  // \n ist newLine
  private void handleTextMessage(@NonNull MasterSecretUnion masterSecret,
                                 @NonNull SignalServiceEnvelope envelope,
                                 @NonNull SignalServiceDataMessage message,
                                 @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    EncryptingSmsDatabase database   = DatabaseFactory.getEncryptingSmsDatabase(context);
    String                body       = message.getBody().isPresent() ? message.getBody().get() : "";
    Recipients            recipients = getMessageDestination(envelope, message);
    String                source = envelope.getSource();

    Recipient             r = recipients.getPrimaryRecipient();

    if (isVCard(body)) {
      String vCardString = body.replace("!@vcard_", "").trim();
//      boolean isFinisher = vCardString.endsWith("|fpf");
//      if(isFinisher) {
//        vCardString = vCardString.replace("|fpf", "").trim();
//      }
      try {
        PendingList.checkExpirationDates(context);
        BlackList.checkExpirationDates(context);

        VCard vCard = JsonUtils.fromJson(vCardString, VCard.class);

        // Ist die Nummer schon in der BlackList vorhanden, verwerfe die Anfrage
        if (BlackList
                .getBlackListContent(context)
                .isInBlackList(vCard.getMobileNumber())) return;

        // Ist die Nummer schon in der WhiteList vorhanden, verwerfe die Anfrage
        if (WhiteList
                .getWhiteListContent(context)
                .isInWhiteList(vCard.getMobileNumber())) return;


        int result = PendingList.addNewVCard(context, vCard);
        if (result > -1) sendPendingToParents(context, vCard, result);
      } catch (IOException ioe) {
        ioe.printStackTrace();
      } finally {
//        if (isFinisher) {
//          final Context appContext = context;
//
//          new AsyncTask<Void, Void, Void>(){
//
//            @Override
//            protected Void doInBackground(Void... params) {
//              Intent intent = new Intent(appContext, ConversationListActivity.class);
//              context.startActivity(intent);
//              return null;
//            }
//          }.execute();
//        }
      }
    }

    // Steffi: Prüfe, ob Sender in der Whitelist ist
    // Wenn nicht, dann droppe Nachricht (sprich: return;)
    if (!isInWhiteList(source)) return;

    // Steffi: Nachricht untersuchen, ob von Eltern stammt und spez. Inhalt zum Blocken (-> Blacklist), Erlauben oder Hinzufügen (-> Whitelist) eines Kontaktes vorhanden ist
    // Wenn ja, dann verarbeite Nachricht als Befehl und droppe danach (Nachricht  ist "unsichtbar")
    if (isFromParents(source)) {
      if (isSpecialMessage(body)) {
        // Prüfe Nachricht auf SpecialCode und verarbeite diesen dann
        HandleCommand(body, source, recipients);
        // Nachricht droppen:
        return;
      }
    }
//     Wenn dennoch ein Spezial-Kommando ankommt, wird es einfach verworfen
    if (isSpecialMessage(body)) {
      return;
    }
    // Ansonsten zeige wie üblich die Nachricht an
    if (message.getExpiresInSeconds() != recipients.getExpireMessages()) {
      return;
      // handleExpirationUpdate(masterSecret, envelope, message, Optional.<Long>absent());
    // Steffi: Nachrichten, die von allein verschwinden sollen, werden gar nicht erst angezeigt
    }

    Long threadId;

    if (smsMessageId.isPresent() && !message.getGroupInfo().isPresent()) {
      threadId = database.updateBundleMessageBody(masterSecret, smsMessageId.get(), body).second;
    } else {
      IncomingTextMessage textMessage = new IncomingTextMessage(envelope.getSource(),
                                                                envelope.getSourceDevice(),
                                                                message.getTimestamp(), body,
                                                                message.getGroupInfo(),
                                                                message.getExpiresInSeconds() * 1000);
// Steffi: Hier wird die Nachricht angezeigt
      textMessage = new IncomingEncryptedMessage(textMessage, body);
      Optional<InsertResult> insertResult = database.insertMessageInbox(masterSecret, textMessage);

      if (insertResult.isPresent()) threadId = insertResult.get().getThreadId();
      else                          threadId = null;

      if (smsMessageId.isPresent()) database.deleteMessage(smsMessageId.get());
    }

    if (threadId != null) {
      MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), threadId);
    }
  }


  private void sendPendingToParents(Context context, VCard vCard, int identifier) {
    String message = "Neuer Freund wartet auf Freigabe\n";
    message += String.format("ID: %1$s - Name: %2$s %3$s, Nummer: %4$s\n", identifier, vCard.getFirstName(), vCard.getLastName(), vCard.getMobileNumber());
    message += "Eltern:";
    for (ParentsContact p : vCard.getParents()) {
      message += "\n";
      message += String.format("Name: %1$s %2$s, Nummer: %3$s", p.getFirstName(), p.getLastName(), p.getMobileNumber());
    }
    message += "\n";
    message += String.format("Sie können diesen Kontakt zulassen, indem Sie \"!@ ok %1$s\" senden\n", identifier);
    message += "Für weitere Funktionen senden Sie \"!@ help\"";

    VCard ownVCard = getPersonalVCard();
    String parentsNumber = ownVCard.getParents().get(0).getMobileNumber();
    Recipients r = RecipientFactory.getRecipientsFromString(context, parentsNumber, false);
    try {
      SendMessage(message, parentsNumber, r);
    } catch (NotInDirectoryException nide) {
      nide.printStackTrace();
    }
  }

  private boolean isVCard(String body) {
    return body.startsWith("!@vcard_");
  }

  private long handleSynchronizeSentTextMessage(@NonNull MasterSecretUnion masterSecret,
                                                @NonNull SentTranscriptMessage message,
                                                @NonNull Optional<Long> smsMessageId)
      throws MmsException
  {
    EncryptingSmsDatabase database            = DatabaseFactory.getEncryptingSmsDatabase(context);
    Recipients            recipients          = getSyncMessageDestination(message);
    String                body                = message.getMessage().getBody().or("");
    long                  expiresInMillis     = message.getMessage().getExpiresInSeconds() * 1000;
    OutgoingTextMessage   outgoingTextMessage = new OutgoingTextMessage(recipients, body, expiresInMillis, -1);

    if (recipients.getExpireMessages() != message.getMessage().getExpiresInSeconds()) {
      handleSynchronizeSentExpirationUpdate(masterSecret, message, Optional.<Long>absent());
    }

    long threadId  = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipients);
    long messageId = database.insertMessageOutbox(masterSecret, threadId, outgoingTextMessage, false, message.getTimestamp(), null);

    database.markAsSent(messageId, true);

    if (smsMessageId.isPresent()) {
      database.deleteMessage(smsMessageId.get());
    }

    if (expiresInMillis > 0) {
      database.markExpireStarted(messageId, message.getExpirationStartTimestamp());
      ApplicationContext.getInstance(context)
                        .getExpiringMessageManager()
                        .scheduleDeletion(messageId, false, message.getExpirationStartTimestamp(), expiresInMillis);
    }

    return threadId;
  }

  private void handleInvalidVersionMessage(@NonNull MasterSecretUnion masterSecret,
                                           @NonNull SignalServiceEnvelope envelope,
                                           @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsInvalidVersionKeyExchange(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsInvalidVersionKeyExchange(smsMessageId.get());
    }
  }

  private void handleCorruptMessage(@NonNull MasterSecretUnion masterSecret,
                                    @NonNull SignalServiceEnvelope envelope,
                                    @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsDecryptFailed(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsDecryptFailed(smsMessageId.get());
    }
  }

  private void handleNoSessionMessage(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceEnvelope envelope,
                                      @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsNoSession(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsNoSession(smsMessageId.get());
    }
  }

  private void handleLegacyMessage(@NonNull MasterSecretUnion masterSecret,
                                   @NonNull SignalServiceEnvelope envelope,
                                   @NonNull Optional<Long> smsMessageId)
  {
    EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);

    if (!smsMessageId.isPresent()) {
      Optional<InsertResult> insertResult = insertPlaceholder(envelope);

      if (insertResult.isPresent()) {
        smsDatabase.markAsLegacyVersion(insertResult.get().getMessageId());
        MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), insertResult.get().getThreadId());
      }
    } else {
      smsDatabase.markAsLegacyVersion(smsMessageId.get());
    }
  }

  private void handleDuplicateMessage(@NonNull MasterSecretUnion masterSecret,
                                      @NonNull SignalServiceEnvelope envelope,
                                      @NonNull Optional<Long> smsMessageId)
  {
    // Let's start ignoring these now
//    SmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
//
//    if (smsMessageId <= 0) {
//      Pair<Long, Long> messageAndThreadId = insertPlaceholder(masterSecret, envelope);
//      smsDatabase.markAsDecryptDuplicate(messageAndThreadId.first);
//      MessageNotifier.updateNotification(context, masterSecret, messageAndThreadId.second);
//    } else {
//      smsDatabase.markAsDecryptDuplicate(smsMessageId);
//    }
  }

  private void handleUntrustedIdentityMessage(@NonNull MasterSecretUnion masterSecret,
                                              @NonNull SignalServiceEnvelope envelope,
                                              @NonNull Optional<Long> smsMessageId)
  {
    try {
      EncryptingSmsDatabase database       = DatabaseFactory.getEncryptingSmsDatabase(context);
      Recipients            recipients     = RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
      long                  recipientId    = recipients.getPrimaryRecipient().getRecipientId();
      byte[]                serialized     = envelope.hasLegacyMessage() ? envelope.getLegacyMessage() : envelope.getContent();
      PreKeySignalMessage   whisperMessage = new PreKeySignalMessage(serialized);
      IdentityKey           identityKey    = whisperMessage.getIdentityKey();
      String                encoded        = Base64.encodeBytes(serialized);

      IncomingTextMessage   textMessage    = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
                                                                     envelope.getTimestamp(), encoded,
                                                                     Optional.<SignalServiceGroup>absent(), 0);

      if (!smsMessageId.isPresent()) {
        IncomingPreKeyBundleMessage bundleMessage = new IncomingPreKeyBundleMessage(textMessage, encoded, envelope.hasLegacyMessage());
        Optional<InsertResult>      insertResult  = database.insertMessageInbox(masterSecret, bundleMessage);

        if (insertResult.isPresent()) {
          database.setMismatchedIdentity(insertResult.get().getMessageId(), recipientId, identityKey);
          MessageNotifier.updateNotification(context, masterSecret.getMasterSecret().orNull(), insertResult.get().getThreadId());
        }
      } else {
        database.updateMessageBody(masterSecret, smsMessageId.get(), encoded);
        database.markAsPreKeyBundle(smsMessageId.get());
        database.setMismatchedIdentity(smsMessageId.get(), recipientId, identityKey);
      }
    } catch (InvalidMessageException | InvalidVersionException e) {
      throw new AssertionError(e);
    }
  }

  private Optional<InsertResult> insertPlaceholder(@NonNull SignalServiceEnvelope envelope) {
    EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
    IncomingTextMessage   textMessage = new IncomingTextMessage(envelope.getSource(), envelope.getSourceDevice(),
                                                                envelope.getTimestamp(), "",
                                                                Optional.<SignalServiceGroup>absent(), 0);

    textMessage = new IncomingEncryptedMessage(textMessage, "");
    return database.insertMessageInbox(textMessage);
  }

  private Recipients getSyncMessageDestination(SentTranscriptMessage message) {
    if (message.getMessage().getGroupInfo().isPresent()) {
      return RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(message.getMessage().getGroupInfo().get().getGroupId()), false);
    } else {
      return RecipientFactory.getRecipientsFromString(context, message.getDestination().get(), false);
    }
  }

  private Recipients getMessageDestination(SignalServiceEnvelope envelope, SignalServiceDataMessage message) {
    if (message.getGroupInfo().isPresent()) {
      return RecipientFactory.getRecipientsFromString(context, GroupUtil.getEncodedId(message.getGroupInfo().get().getGroupId()), false);
    } else {
      return RecipientFactory.getRecipientsFromString(context, envelope.getSource(), false);
    }
  }
}
