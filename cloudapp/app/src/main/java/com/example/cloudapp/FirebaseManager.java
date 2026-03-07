package com.example.cloudapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public final class FirebaseManager {

    private FirebaseManager() {
    }

    public static FirebaseAuth getAuth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFirestore getFirestore() {
        return FirebaseFirestore.getInstance();
    }
}
