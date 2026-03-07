package com.example.cloudapp.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cloudapp.FirebaseManager;
import com.example.cloudapp.model.Panier;
import com.example.cloudapp.model.Reservation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PanierRepository {
    private static final String TAG = "PanierRepository";

    public interface PaniersListener {
        void onChanged(List<Panier> paniers);

        void onError(Exception e);
    }

    public interface ReservationCallback {
        void onSuccess();

        void onError(Exception e);
    }

    public interface ReservationsListener {
        void onChanged(List<Reservation> reservations);

        void onError(Exception e);
    }

    public interface UserReservationsCallback {
        void onSuccess(List<Reservation> reservations);

        void onError(Exception e);
    }

    public interface StatsCallback {
        void onSuccess(ReservationStats stats);

        void onError(Exception e);
    }

    public interface UserCallback {
        void onSuccess();

        void onError(Exception e);
    }

    public static class ReservationStats {
        private final int soldCount;
        private final double totalRevenue;

        public ReservationStats(int soldCount, double totalRevenue) {
            this.soldCount = soldCount;
            this.totalRevenue = totalRevenue;
        }

        public int getSoldCount() {
            return soldCount;
        }

        public double getTotalRevenue() {
            return totalRevenue;
        }
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final CollectionReference paniersCollection;
    private final CollectionReference reservationsCollection;
    private final CollectionReference usersCollection;

    public PanierRepository() {
        db = FirebaseManager.getFirestore();
        auth = FirebaseManager.getAuth();
        paniersCollection = db.collection("paniers");
        reservationsCollection = db.collection("reservations");
        usersCollection = db.collection("users");
    }

    public ListenerRegistration listenAvailablePaniers(@NonNull PaniersListener listener) {
        // Real-time updates: every add/update/delete in Firestore triggers this listener.
        return paniersCollection
                .whereEqualTo("available", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    List<Panier> paniers = mapPaniers(value);
                    listener.onChanged(paniers);
                });
    }

    public void createUserProfile(
            @NonNull String userId,
            @NonNull String name,
            @NonNull String email,
            @Nullable UserCallback callback
    ) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("role", "client");

        usersCollection.document(userId)
                .set(userData)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "User profile created: " + userId);
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user profile for: " + userId, e);
                    if (callback != null) {
                        callback.onError(e);
                    }
                });
    }

    public void reservePanier(@NonNull String panierId, @NonNull ReservationCallback callback) {
        if (panierId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Panier id is required"));
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }

        String userId = user.getUid();
        DocumentReference panierRef = paniersCollection.document(panierId);
        DocumentReference reservationRef = reservationsCollection.document();

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(panierRef);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Panier not found");
            }

            Long currentQuantity = snapshot.getLong("quantity");
            String commerceName = snapshot.getString("commerceName");
            Double price = snapshot.getDouble("price");
            Boolean available = snapshot.getBoolean("available");
            long safeQuantity = currentQuantity == null ? 0L : currentQuantity;
            boolean safeAvailable = available != null && available;

            // Guard reservation when stock is empty or marked unavailable.
            if (!safeAvailable || safeQuantity <= 0) {
                throw new IllegalStateException("This panier is no longer available.");
            }

            long newQuantity = safeQuantity - 1;

            Map<String, Object> reservationData = new HashMap<>();
            reservationData.put("userId", userId);
            reservationData.put("panierId", panierId);
            reservationData.put("commerceName", commerceName == null ? "" : commerceName);
            reservationData.put("price", price == null ? 0.0 : price);
            reservationData.put("date", FieldValue.serverTimestamp());
            reservationData.put("status", "confirmed");

            transaction.set(reservationRef, reservationData);
            transaction.update(panierRef, "quantity", newQuantity);
            transaction.update(panierRef, "available", newQuantity > 0);

            Log.d(
                    TAG,
                    "Transaction prepared. panierId=" + panierId
                            + ", reservationId=" + reservationRef.getId()
                            + ", newQuantity=" + newQuantity
            );
            return null;
        }).addOnSuccessListener(unused -> {
                    Log.d(TAG, "Reservation confirmed: " + reservationRef.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Reservation failed for panierId=" + panierId, e);
                    callback.onError(e);
                });
    }

    public void reservePanier(@NonNull String userId, @NonNull Panier panier, @NonNull ReservationCallback callback) {
        if (panier.getId() == null || panier.getId().isEmpty()) {
            callback.onError(new IllegalArgumentException("Panier id is required"));
            return;
        }
        reservePanier(panier.getId(), callback);
    }

    public ListenerRegistration listenMyReservations(@NonNull ReservationsListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            listener.onError(new IllegalStateException("User is not authenticated"));
            return null;
        }

        return reservationsCollection
                .whereEqualTo("userId", user.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }

                    List<Reservation> reservations = mapReservations(value);
                    reservations.sort((first, second) -> {
                        if (first.getDate() == null && second.getDate() == null) {
                            return 0;
                        }
                        if (first.getDate() == null) {
                            return 1;
                        }
                        if (second.getDate() == null) {
                            return -1;
                        }
                        return second.getDate().compareTo(first.getDate());
                    });
                    listener.onChanged(reservations);
                });
    }

    public void getUserReservations(@NonNull UserReservationsCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }

        reservationsCollection
                .whereEqualTo("userId", user.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Reservation> reservations = mapReservations(querySnapshot);
                    reservations.sort((first, second) -> {
                        if (first.getDate() == null && second.getDate() == null) {
                            return 0;
                        }
                        if (first.getDate() == null) {
                            return 1;
                        }
                        if (second.getDate() == null) {
                            return -1;
                        }
                        return second.getDate().compareTo(first.getDate());
                    });
                    callback.onSuccess(reservations);
                })
                .addOnFailureListener(callback::onError);
    }

    public void cancelReservation(@NonNull String reservationId, @NonNull ReservationCallback callback) {
        if (reservationId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Reservation id is required"));
            return;
        }

        DocumentReference reservationRef = reservationsCollection.document(reservationId);

        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot reservationSnapshot = transaction.get(reservationRef);
            if (!reservationSnapshot.exists()) {
                throw new IllegalStateException("Reservation not found");
            }

            String status = reservationSnapshot.getString("status");
            if ("cancelled".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Reservation already cancelled");
            }
            if ("paid".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Paid reservation cannot be cancelled");
            }

            String panierId = reservationSnapshot.getString("panierId");
            if (panierId == null || panierId.isEmpty()) {
                throw new IllegalStateException("Invalid panier id in reservation");
            }

            DocumentReference panierRef = paniersCollection.document(panierId);
            DocumentSnapshot panierSnapshot = transaction.get(panierRef);
            long quantity = 0L;
            if (panierSnapshot.exists()) {
                Long currentQuantity = panierSnapshot.getLong("quantity");
                quantity = currentQuantity == null ? 0L : currentQuantity;
            }

            transaction.delete(reservationRef);
            transaction.update(panierRef, "quantity", quantity + 1);
            transaction.update(panierRef, "available", true);

            Log.d(TAG, "Reservation deleted and stock restored: " + reservationId);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to cancel reservation: " + reservationId, e);
                    callback.onError(e);
                });
    }

    public void payReservation(@NonNull String reservationId, @NonNull ReservationCallback callback) {
        if (reservationId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Reservation id is required"));
            return;
        }

        reservationsCollection.document(reservationId)
                .update("status", "paid")
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Reservation paid: " + reservationId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to pay reservation: " + reservationId, e);
                    callback.onError(e);
                });
    }

    public void getReservationStats(@NonNull StatsCallback callback) {
        reservationsCollection
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int soldCount = 0;
                    double totalRevenue = 0.0;

                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        String status = document.getString("status");
                        if ("cancelled".equalsIgnoreCase(status)) {
                            continue;
                        }

                        soldCount++;
                        Double price = document.getDouble("price");
                        totalRevenue += price == null ? 0.0 : price;
                    }

                    callback.onSuccess(new ReservationStats(soldCount, totalRevenue));
                })
                .addOnFailureListener(callback::onError);
    }

    private List<Panier> mapPaniers(QuerySnapshot value) {
        List<Panier> paniers = new ArrayList<>();
        if (value == null) {
            return paniers;
        }

        value.getDocuments().forEach(document -> {
            Panier panier = document.toObject(Panier.class);
            if (panier != null) {
                panier.setId(document.getId());
                paniers.add(panier);
            }
        });
        return paniers;
    }

    private List<Reservation> mapReservations(QuerySnapshot value) {
        List<Reservation> reservations = new ArrayList<>();
        if (value == null) {
            return reservations;
        }

        value.getDocuments().forEach(document -> {
            Reservation reservation = document.toObject(Reservation.class);
            if (reservation != null) {
                reservation.setId(document.getId());
                reservations.add(reservation);
            }
        });
        return reservations;
    }
}
