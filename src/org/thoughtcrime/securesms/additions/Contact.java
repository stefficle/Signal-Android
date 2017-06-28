package org.thoughtcrime.securesms.additions;


/**
 * Abstrakte Klasse zur Definition von Kontakten
 */
public abstract class Contact {
    private String firstName;
    private String lastName;
    private String mobileNumber;

    public Contact() {
    }

    public Contact(String firstName, String lastName, String mobileNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.mobileNumber = mobileNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    /**
     * Objekt-Vergleich findet anhand der Mobil-Nummer statt (da diese für gewöhnlich einmalig ist).
     *
     * @param o Objekt, welches verglichen werden soll
     * @return TRUE, wenn es die gleichen Objekte sind.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Contact contact = (Contact) o;

        return mobileNumber.equals(contact.mobileNumber);

    }

    @Override
    public int hashCode() {
        return mobileNumber.hashCode();
    }
}
