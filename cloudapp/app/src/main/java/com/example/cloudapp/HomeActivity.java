package com.example.cloudapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cloudapp.adapter.PanierAdapter;
import com.example.cloudapp.model.Panier;
import com.example.cloudapp.viewmodel.HomeViewModel;

import java.util.HashSet;
import java.util.Set;

public class HomeActivity extends AppCompatActivity implements PanierAdapter.OnReserveClickListener {

    private HomeViewModel viewModel;
    private PanierAdapter panierAdapter;
    private ProgressBar progressBar;
    private final Set<String> knownPanierIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        NotificationHelper.requestPermissionIfNeeded(this);
        NotificationHelper.ensureChannel(this);
        FcmTokenManager.syncCurrentUserToken();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        RecyclerView recyclerView = findViewById(R.id.recyclerPaniers);
        Button btnMyReservations = findViewById(R.id.btnMyReservations);
        Button btnProfile = findViewById(R.id.btnProfile);
        progressBar = findViewById(R.id.progressBar);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        panierAdapter = new PanierAdapter(this);
        recyclerView.setAdapter(panierAdapter);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        observeViewModel();
        viewModel.startPaniersListener();

        btnMyReservations.setOnClickListener(v -> {
            startActivity(new Intent(this, MyReservationsActivity.class));
        });

        btnProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });
    }

    private void observeViewModel() {
        viewModel.getPaniersLiveData().observe(this, paniers -> {
            progressBar.setVisibility(View.GONE);
            panierAdapter.submitList(paniers);
            notifyAvailablePaniers(paniers);
        });

        viewModel.getErrorLiveData().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getReservationSuccessLiveData().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, "Reservation created", Toast.LENGTH_SHORT).show();
                NotificationHelper.showNotification(
                        this,
                        (int) System.currentTimeMillis(),
                        "Reservation confirmee",
                        "Votre reservation a ete confirmee."
                );
            }
        });
    }

    private void notifyAvailablePaniers(java.util.List<Panier> paniers) {
        for (Panier panier : paniers) {
            String id = panier.getId();
            if (id == null || !panier.isAvailable()) {
                continue;
            }
            if (!knownPanierIds.contains(id)) {
                knownPanierIds.add(id);
                NotificationHelper.showNotification(
                        this,
                        id.hashCode(),
                        "Panier disponible",
                        "Nouveau panier disponible chez " + panier.getCommerceName()
                );
            }
        }
    }

    @Override
    public void onReserveClicked(Panier panier) {
        if (FirebaseManager.getAuth().getCurrentUser() == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.reservePanier(panier);
    }
}
