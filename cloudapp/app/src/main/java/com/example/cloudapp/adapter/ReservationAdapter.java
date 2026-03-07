package com.example.cloudapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cloudapp.R;
import com.example.cloudapp.model.Reservation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReservationAdapter extends RecyclerView.Adapter<ReservationAdapter.ReservationViewHolder> {

    public interface ReservationActionListener {
        void onCancelClicked(Reservation reservation);

        void onPayClicked(Reservation reservation);
    }

    private final List<Reservation> reservations = new ArrayList<>();
    private final ReservationActionListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public ReservationAdapter(@NonNull ReservationActionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Reservation> newReservations) {
        reservations.clear();
        if (newReservations != null) {
            reservations.addAll(newReservations);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReservationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reservation, parent, false);
        return new ReservationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReservationViewHolder holder, int position) {
        Reservation reservation = reservations.get(position);
        holder.tvCommerceName.setText(reservation.getCommerceName() == null ? "Unknown commerce" : reservation.getCommerceName());
        holder.tvPrice.setText(String.format(Locale.getDefault(), "Price: %.2f", reservation.getPrice()));
        holder.tvStatus.setText(String.format(Locale.getDefault(), "Status: %s", reservation.getStatus()));
        holder.tvDate.setText(
                reservation.getDate() == null
                        ? "Date: -"
                        : "Date: " + dateFormat.format(reservation.getDate().toDate())
        );

        boolean isCancelled = "cancelled".equalsIgnoreCase(reservation.getStatus());
        boolean isPaid = "paid".equalsIgnoreCase(reservation.getStatus());

        holder.btnCancel.setEnabled(!isCancelled && !isPaid);
        holder.btnPay.setEnabled(!isPaid && !isCancelled);

        holder.btnCancel.setOnClickListener(v -> listener.onCancelClicked(reservation));
        holder.btnPay.setOnClickListener(v -> listener.onPayClicked(reservation));
    }

    @Override
    public int getItemCount() {
        return reservations.size();
    }

    static class ReservationViewHolder extends RecyclerView.ViewHolder {
        TextView tvCommerceName;
        TextView tvPrice;
        TextView tvDate;
        TextView tvStatus;
        Button btnCancel;
        Button btnPay;

        ReservationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCommerceName = itemView.findViewById(R.id.tvReservationCommerceName);
            tvPrice = itemView.findViewById(R.id.tvReservationPrice);
            tvDate = itemView.findViewById(R.id.tvReservationDate);
            tvStatus = itemView.findViewById(R.id.tvReservationStatus);
            btnCancel = itemView.findViewById(R.id.btnCancelReservation);
            btnPay = itemView.findViewById(R.id.btnPayReservation);
        }
    }
}
