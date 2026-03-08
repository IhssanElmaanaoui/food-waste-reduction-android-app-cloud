package com.example.cloudapp;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cloudapp.adapter.ReservationAdapter;
import com.example.cloudapp.data.PanierRepository;
import com.example.cloudapp.model.Reservation;
import com.google.firebase.firestore.ListenerRegistration;

public class MyReservationsActivity extends AppCompatActivity implements ReservationAdapter.ReservationActionListener {

    private PanierRepository repository;
    private ReservationAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private ListenerRegistration reservationsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reservations);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        RecyclerView recyclerView = findViewById(R.id.recyclerReservations);
        progressBar = findViewById(R.id.progressReservations);
        tvEmptyState = findViewById(R.id.tvEmptyReservations);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReservationAdapter(this);
        recyclerView.setAdapter(adapter);

        repository = new PanierRepository();
        startReservationsListener();
    }

    private void startReservationsListener() {
        progressBar.setVisibility(View.VISIBLE);
        reservationsListener = repository.listenMyReservations(new PanierRepository.ReservationsListener() {
            @Override
            public void onChanged(java.util.List<Reservation> reservations) {
                progressBar.setVisibility(View.GONE);
                adapter.submitList(reservations);
                tvEmptyState.setVisibility(reservations.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MyReservationsActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onCancelClicked(Reservation reservation) {
        if (reservation.getId() == null) {
            Toast.makeText(this, "Reservation id missing", Toast.LENGTH_SHORT).show();
            return;
        }

        repository.cancelReservation(reservation.getId(), new PanierRepository.ReservationCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(MyReservationsActivity.this, "Reservation cancelled", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MyReservationsActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPayClicked(Reservation reservation) {
        if (reservation.getId() == null) {
            Toast.makeText(this, "Reservation id missing", Toast.LENGTH_SHORT).show();
            return;
        }

        repository.payReservation(reservation.getId(), new PanierRepository.ReservationCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(MyReservationsActivity.this, "Payment successful", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MyReservationsActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (reservationsListener != null) {
            reservationsListener.remove();
        }
        super.onDestroy();
    }
}
