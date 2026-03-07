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

public class HomeActivity extends AppCompatActivity implements PanierAdapter.OnReserveClickListener {

    private HomeViewModel viewModel;
    private PanierAdapter panierAdapter;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        RecyclerView recyclerView = findViewById(R.id.recyclerPaniers);
        Button btnMyReservations = findViewById(R.id.btnMyReservations);
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
    }

    private void observeViewModel() {
        viewModel.getPaniersLiveData().observe(this, paniers -> {
            progressBar.setVisibility(View.GONE);
            panierAdapter.submitList(paniers);
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
            }
        });
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
