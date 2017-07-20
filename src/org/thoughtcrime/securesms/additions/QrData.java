package org.thoughtcrime.securesms.additions;

import java.util.UUID;

/**
 * Created by MayBell on 20.07.2017.
 */

public class QrData {
    private UUID ownId;
    private UUID otherId;
    private String mobileNumber;

    public QrData() { }

    public QrData(UUID ownId, UUID otherId, String mobileNumber) {
        this.ownId = ownId;
        this.otherId = otherId;
        this.mobileNumber = mobileNumber;
    }

    public UUID getOwnId() { return this.ownId; }

    public UUID getOtherId() { return this.otherId; }

    public String getMobileNumber() { return this.getMobileNumber(); }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        QrData qrData = (QrData) o;

        return qrData.getOwnId() == this.ownId
                && qrData.getOtherId() == this.otherId
                && this.getMobileNumber() == this.mobileNumber;
    }

    @Override
    public int hashCode() {
        return this.getOwnId().hashCode()
                ^ this.getOtherId().hashCode()
                ^ this.getMobileNumber().hashCode();
    }
}
