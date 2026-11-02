package com.kavacon.walletsecure;

/**
 * Modelo de tarjeta — datos ficticios de prueba. Contraparte de
 * com.kavacon.walletvulnerable.Card para la demo educativa de Kavacon.
 */
public class Card {
    public final String brand;
    public final String fullNumber; // solo en memoria/storage cifrado, nunca se loguea
    public final String last4;
    public final String holder;
    public final String expiry;

    public Card(String brand, String fullNumber, String holder, String expiry) {
        this.brand = brand;
        this.fullNumber = fullNumber;
        this.last4 = fullNumber.replaceAll("\\s", "")
                .substring(Math.max(0, fullNumber.replaceAll("\\s", "").length() - 4));
        this.holder = holder;
        this.expiry = expiry;
    }

    public String maskedNumber() {
        return "•••• •••• •••• " + last4;
    }
}
