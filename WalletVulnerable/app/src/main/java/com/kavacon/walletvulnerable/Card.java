package com.kavacon.walletvulnerable;

/**
 * Modelo de tarjeta — datos 100% ficticios (números de prueba estándar de la industria,
 * no corresponden a tarjetas reales). Solo para la demo educativa de Kavacon.
 */
public class Card {
    public final String brand;
    public final String number;
    public final String holder;
    public final String expiry;
    public final String cvv;

    public Card(String brand, String number, String holder, String expiry, String cvv) {
        this.brand = brand;
        this.number = number;
        this.holder = holder;
        this.expiry = expiry;
        this.cvv = cvv;
    }
}
