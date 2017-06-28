package org.thoughtcrime.securesms.additions;


/**
 * Definiert einen Eltern-Kontakt eines Kindes
 */
public class ParentsContact extends Contact {
    public ParentsContact(String firstName, String lastName, String mobileNumber) {
        super(firstName, lastName, mobileNumber);
    }

    public ParentsContact() {
    }

    public ParentsContact(ParentsContact parentsContact) {
        this.setFirstName(parentsContact.getFirstName());
        this.setLastName(parentsContact.getLastName());
        this.setMobileNumber(parentsContact.getMobileNumber());
    }
}
