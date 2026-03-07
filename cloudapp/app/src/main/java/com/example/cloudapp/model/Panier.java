package com.example.cloudapp.model;

public class Panier {
    private String id;
    private String commerceName;
    private String description;
    private double price;
    private long quantity;
    private boolean available;

    public Panier() {
    }

    public Panier(
            String id,
            String commerceName,
            String description,
            double price,
            long quantity,
            boolean available
    ) {
        this.id = id;
        this.commerceName = commerceName;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.available = available;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCommerceName() {
        return commerceName;
    }

    public void setCommerceName(String commerceName) {
        this.commerceName = commerceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
