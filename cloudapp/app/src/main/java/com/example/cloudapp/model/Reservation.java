package com.example.cloudapp.model;

import com.google.firebase.Timestamp;

public class Reservation {
    private String id;
    private String userId;
    private String merchantId;
    private String panierId;
    private String commerceName;
    private double price;
    private Timestamp date;
    private String status;

    public Reservation() {
    }

    public Reservation(
            String id,
            String userId,
            String merchantId,
            String panierId,
            String commerceName,
            double price,
            Timestamp date,
            String status
    ) {
        this.id = id;
        this.userId = userId;
        this.merchantId = merchantId;
        this.panierId = panierId;
        this.commerceName = commerceName;
        this.price = price;
        this.date = date;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getPanierId() {
        return panierId;
    }

    public void setPanierId(String panierId) {
        this.panierId = panierId;
    }

    public String getCommerceName() {
        return commerceName;
    }

    public void setCommerceName(String commerceName) {
        this.commerceName = commerceName;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Timestamp getDate() {
        return date;
    }

    public void setDate(Timestamp date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
