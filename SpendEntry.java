package com.java.moneyspendapp;

public class SpendEntry {

    public String date;
    public String amount;
    public String cause;

    // Default constructor required for calls to DataSnapshot.getValue(SpendEntry.class)
    public SpendEntry() {
    }
    public SpendEntry(String date, String amount, String cause) {
        this.date = date;
        this.amount = amount;
        this.cause = cause;
    }
}
