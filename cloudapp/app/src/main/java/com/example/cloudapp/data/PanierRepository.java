package com.example.cloudapp.data;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cloudapp.CloudApp;
import com.example.cloudapp.FirebaseManager;
import com.example.cloudapp.cache.AppDatabase;
import com.example.cloudapp.cache.CachePanierDao;
import com.example.cloudapp.cache.CachePanierEntity;
import com.example.cloudapp.cache.CacheReservationDao;
import com.example.cloudapp.cache.CacheReservationEntity;
import com.example.cloudapp.model.Panier;
import com.example.cloudapp.model.Reservation;
import com.example.cloudapp.model.User;
import com.google.firebase.Timestamp;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    public interface RoleCallback {
        void onSuccess(String role, boolean approved);

        void onError(Exception e);
    }

    public interface UsersListener {
        void onChanged(List<User> users);

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

    private final CachePanierDao cachePanierDao;
    private final CacheReservationDao cacheReservationDao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public PanierRepository() {
        db = FirebaseManager.getFirestore();
        auth = FirebaseManager.getAuth();
        paniersCollection = db.collection("paniers");
        reservationsCollection = db.collection("reservations");
        usersCollection = db.collection("users");

        AppDatabase appDatabase = AppDatabase.getInstance(CloudApp.getAppContext());
        cachePanierDao = appDatabase.cachePanierDao();
        cacheReservationDao = appDatabase.cacheReservationDao();
    }

    public ListenerRegistration listenAvailablePaniers(@NonNull PaniersListener listener) {
        return paniersCollection
                .whereEqualTo("available", true)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        ioExecutor.execute(() -> {
                            List<Panier> cached = mapCachedPaniers(cachePanierDao.getAvailablePaniers());
                            if (!cached.isEmpty()) {
                                listener.onChanged(cached);
                            }
                            listener.onError(error);
                        });
                        return;
                    }

                    List<Panier> paniers = mapPaniers(value);
                    cachePaniers(paniers);
                    listener.onChanged(paniers);
                });
    }

    public ListenerRegistration listenMerchantPaniers(@NonNull String merchantId, @NonNull PaniersListener listener) {
        return paniersCollection
                .whereEqualTo("merchantId", merchantId)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        ioExecutor.execute(() -> {
                            List<Panier> cached = mapCachedPaniers(cachePanierDao.getMerchantPaniers(merchantId));
                            if (!cached.isEmpty()) {
                                listener.onChanged(cached);
                            }
                            listener.onError(error);
                        });
                        return;
                    }

                    List<Panier> paniers = mapPaniers(value);
                    cachePaniers(paniers);
                    listener.onChanged(paniers);
                });
    }

    public void createUserProfile(
            @NonNull String userId,
            @NonNull String name,
            @NonNull String email,
            @Nullable UserCallback callback
    ) {
        createUserProfile(userId, name, email, "client", callback);
    }

    public void createUserProfile(
            @NonNull String userId,
            @NonNull String name,
            @NonNull String email,
            @NonNull String role,
            @Nullable UserCallback callback
    ) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("email", email);
        userData.put("role", role);
        // Nouveaux commerçants approuvés par défaut pour pouvoir tester l'interface sans passer par l'admin
        userData.put("approved", true);

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

    public void getCurrentUserRole(@NonNull RoleCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }

        usersCollection.document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    String role = snapshot.getString("role");
                    if (role != null) role = role.trim().toLowerCase();
                    Boolean approved = snapshot.getBoolean("approved");
                    callback.onSuccess(role == null || role.isEmpty() ? "client" : role, approved == null || approved);
                })
                .addOnFailureListener(callback::onError);
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
            String merchantId = snapshot.getString("merchantId");
            long safeQuantity = currentQuantity == null ? 0L : currentQuantity;
            boolean safeAvailable = available != null && available;

            if (!safeAvailable || safeQuantity <= 0) {
                throw new IllegalStateException("This panier is no longer available.");
            }

            long newQuantity = safeQuantity - 1;

            Map<String, Object> reservationData = new HashMap<>();
            reservationData.put("userId", userId);
            reservationData.put("merchantId", merchantId == null ? "" : merchantId);
            reservationData.put("panierId", panierId);
            reservationData.put("commerceName", commerceName == null ? "" : commerceName);
            reservationData.put("price", price == null ? 0.0 : price);
            reservationData.put("date", FieldValue.serverTimestamp());
            reservationData.put("status", "confirmed");

            transaction.set(reservationRef, reservationData);
            transaction.update(panierRef, "quantity", newQuantity);
            transaction.update(panierRef, "available", newQuantity > 0);
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
                        ioExecutor.execute(() -> {
                            List<Reservation> cached = mapCachedReservations(cacheReservationDao.getByUserId(user.getUid()));
                            if (!cached.isEmpty()) {
                                listener.onChanged(cached);
                            }
                            listener.onError(error);
                        });
                        return;
                    }

                    List<Reservation> reservations = mapReservations(value);
                    sortReservations(reservations);
                    cacheReservations(reservations);
                    listener.onChanged(reservations);
                });
    }

    public ListenerRegistration listenMerchantReservations(@NonNull ReservationsListener listener) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            listener.onError(new IllegalStateException("User is not authenticated"));
            return null;
        }

        return reservationsCollection
                .whereEqualTo("merchantId", user.getUid())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        ioExecutor.execute(() -> {
                            List<Reservation> cached = mapCachedReservations(cacheReservationDao.getByMerchantId(user.getUid()));
                            if (!cached.isEmpty()) {
                                listener.onChanged(cached);
                            }
                            listener.onError(error);
                        });
                        return;
                    }

                    List<Reservation> reservations = mapReservations(value);
                    sortReservations(reservations);
                    cacheReservations(reservations);
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
                    sortReservations(reservations);
                    cacheReservations(reservations);
                    callback.onSuccess(reservations);
                })
                .addOnFailureListener(e -> ioExecutor.execute(() -> {
                    List<Reservation> cached = mapCachedReservations(cacheReservationDao.getByUserId(user.getUid()));
                    if (!cached.isEmpty()) {
                        callback.onSuccess(cached);
                    } else {
                        callback.onError(e);
                    }
                }));
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

            transaction.update(reservationRef, "status", "cancelled");
            transaction.update(panierRef, "quantity", quantity + 1);
            transaction.update(panierRef, "available", true);

            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void payReservation(@NonNull String reservationId, @NonNull ReservationCallback callback) {
        if (reservationId.isEmpty()) {
            callback.onError(new IllegalArgumentException("Reservation id is required"));
            return;
        }

        reservationsCollection.document(reservationId)
                .update("status", "paid")
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void createPanierForMerchant(@NonNull Panier panier, @NonNull ReservationCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("merchantId", user.getUid());
        data.put("commerceName", panier.getCommerceName());
        data.put("description", panier.getDescription());
        data.put("price", panier.getPrice());
        data.put("quantity", panier.getQuantity());
        data.put("available", panier.getQuantity() > 0);

        paniersCollection.add(data)
                .addOnSuccessListener(doc -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void updatePanierForMerchant(@NonNull Panier panier, @NonNull ReservationCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }
        if (panier.getId() == null || panier.getId().trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Panier id is required"));
            return;
        }

        DocumentReference ref = paniersCollection.document(panier.getId());
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Panier not found");
            }
            String owner = snapshot.getString("merchantId");
            if (owner == null || !owner.equals(user.getUid())) {
                throw new IllegalStateException("Forbidden action");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("commerceName", panier.getCommerceName());
            updates.put("description", panier.getDescription());
            updates.put("price", panier.getPrice());
            updates.put("quantity", panier.getQuantity());
            updates.put("available", panier.getQuantity() > 0);
            transaction.update(ref, updates);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void deletePanierForMerchant(@NonNull String panierId, @NonNull ReservationCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }

        DocumentReference ref = paniersCollection.document(panierId);
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                throw new IllegalStateException("Panier not found");
            }
            String owner = snapshot.getString("merchantId");
            if (owner == null || !owner.equals(user.getUid())) {
                throw new IllegalStateException("Forbidden action");
            }
            transaction.delete(ref);
            return null;
        }).addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    public void getReservationStats(@NonNull StatsCallback callback) {
        reservationsCollection
                .get()
                .addOnSuccessListener(querySnapshot -> callback.onSuccess(buildStats(querySnapshot.getDocuments(), null)))
                .addOnFailureListener(callback::onError);
    }

    public void getMerchantReservationStats(@NonNull StatsCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("User is not authenticated"));
            return;
        }

        reservationsCollection
                .whereEqualTo("merchantId", user.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> callback.onSuccess(buildStats(querySnapshot.getDocuments(), user.getUid())))
                .addOnFailureListener(callback::onError);
    }

    public ListenerRegistration listenAllUsers(@NonNull UsersListener listener) {
        return usersCollection.addSnapshotListener((value, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            listener.onChanged(mapUsers(value));
        });
    }

    public void updateUserRole(@NonNull String userId, @NonNull String role, boolean approved, @NonNull UserCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", role);
        updates.put("approved", approved);
        usersCollection.document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    private ReservationStats buildStats(List<DocumentSnapshot> documents, @Nullable String merchantId) {
        int soldCount = 0;
        double totalRevenue = 0.0;

        for (DocumentSnapshot document : documents) {
            if (merchantId != null) {
                String reservationMerchant = document.getString("merchantId");
                if (reservationMerchant == null || !reservationMerchant.equals(merchantId)) {
                    continue;
                }
            }

            String status = document.getString("status");
            if ("cancelled".equalsIgnoreCase(status)) {
                continue;
            }

            soldCount++;
            Double price = document.getDouble("price");
            totalRevenue += price == null ? 0.0 : price;
        }

        return new ReservationStats(soldCount, totalRevenue);
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

    private List<User> mapUsers(QuerySnapshot value) {
        List<User> users = new ArrayList<>();
        if (value == null) {
            return users;
        }

        value.getDocuments().forEach(document -> {
            User user = document.toObject(User.class);
            if (user != null) {
                user.setId(document.getId());
                users.add(user);
            }
        });
        return users;
    }

    private void sortReservations(List<Reservation> reservations) {
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
    }

    private void cachePaniers(List<Panier> paniers) {
        ioExecutor.execute(() -> {
            List<CachePanierEntity> entities = new ArrayList<>();
            for (Panier panier : paniers) {
                CachePanierEntity entity = new CachePanierEntity();
                entity.id = panier.getId() == null ? "" : panier.getId();
                entity.merchantId = panier.getMerchantId();
                entity.commerceName = panier.getCommerceName();
                entity.description = panier.getDescription();
                entity.price = panier.getPrice();
                entity.quantity = panier.getQuantity();
                entity.available = panier.isAvailable();
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                cachePanierDao.insertAll(entities);
            }
        });
    }

    private void cacheReservations(List<Reservation> reservations) {
        ioExecutor.execute(() -> {
            List<CacheReservationEntity> entities = new ArrayList<>();
            for (Reservation reservation : reservations) {
                CacheReservationEntity entity = new CacheReservationEntity();
                entity.id = reservation.getId() == null ? "" : reservation.getId();
                entity.userId = reservation.getUserId();
                entity.merchantId = reservation.getMerchantId();
                entity.panierId = reservation.getPanierId();
                entity.commerceName = reservation.getCommerceName();
                entity.price = reservation.getPrice();
                Timestamp date = reservation.getDate();
                entity.dateMillis = date == null ? 0L : date.toDate().getTime();
                entity.status = reservation.getStatus();
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                cacheReservationDao.insertAll(entities);
            }
        });
    }

    private List<Panier> mapCachedPaniers(List<CachePanierEntity> cached) {
        List<Panier> paniers = new ArrayList<>();
        for (CachePanierEntity entity : cached) {
            Panier panier = new Panier();
            panier.setId(entity.id);
            panier.setMerchantId(entity.merchantId);
            panier.setCommerceName(entity.commerceName);
            panier.setDescription(entity.description);
            panier.setPrice(entity.price);
            panier.setQuantity(entity.quantity);
            panier.setAvailable(entity.available);
            paniers.add(panier);
        }
        return paniers;
    }

    private List<Reservation> mapCachedReservations(List<CacheReservationEntity> cached) {
        List<Reservation> reservations = new ArrayList<>();
        for (CacheReservationEntity entity : cached) {
            Reservation reservation = new Reservation();
            reservation.setId(entity.id);
            reservation.setUserId(entity.userId);
            reservation.setMerchantId(entity.merchantId);
            reservation.setPanierId(entity.panierId);
            reservation.setCommerceName(entity.commerceName);
            reservation.setPrice(entity.price);
            reservation.setDate(new Timestamp(entity.dateMillis / 1000, (int) ((entity.dateMillis % 1000) * 1000000)));
            reservation.setStatus(entity.status);
            reservations.add(reservation);
        }
        return reservations;
    }
}
