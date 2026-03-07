package com.example.cloudapp.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.cloudapp.data.PanierRepository;
import com.example.cloudapp.model.Panier;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class HomeViewModel extends ViewModel {
    private final PanierRepository repository = new PanierRepository();
    private final MutableLiveData<List<Panier>> paniersLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> reservationSuccessLiveData = new MutableLiveData<>();
    private ListenerRegistration listenerRegistration;

    public LiveData<List<Panier>> getPaniersLiveData() {
        return paniersLiveData;
    }

    public LiveData<String> getErrorLiveData() {
        return errorLiveData;
    }

    public LiveData<Boolean> getReservationSuccessLiveData() {
        return reservationSuccessLiveData;
    }

    public void startPaniersListener() {
        if (listenerRegistration != null) {
            return;
        }

        listenerRegistration = repository.listenAvailablePaniers(new PanierRepository.PaniersListener() {
            @Override
            public void onChanged(List<Panier> paniers) {
                paniersLiveData.postValue(paniers);
            }

            @Override
            public void onError(Exception e) {
                errorLiveData.postValue(e.getMessage());
            }
        });
    }

    public void reservePanier(Panier panier) {
        if (panier.getId() == null || panier.getId().isEmpty()) {
            errorLiveData.postValue("Panier id is required");
            return;
        }

        repository.reservePanier(panier.getId(), new PanierRepository.ReservationCallback() {
            @Override
            public void onSuccess() {
                reservationSuccessLiveData.postValue(true);
            }

            @Override
            public void onError(Exception e) {
                errorLiveData.postValue(e.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }
}
