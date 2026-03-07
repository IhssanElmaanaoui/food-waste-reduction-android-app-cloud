package com.example.cloudapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cloudapp.R;
import com.example.cloudapp.model.Panier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PanierAdapter extends RecyclerView.Adapter<PanierAdapter.PanierViewHolder> {

    public interface OnReserveClickListener {
        void onReserveClicked(Panier panier);
    }

    private final List<Panier> paniers = new ArrayList<>();
    private final OnReserveClickListener onReserveClickListener;

    public PanierAdapter(OnReserveClickListener onReserveClickListener) {
        this.onReserveClickListener = onReserveClickListener;
    }

    public void submitList(List<Panier> newPaniers) {
        paniers.clear();
        if (newPaniers != null) {
            paniers.addAll(newPaniers);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PanierViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_panier, parent, false);
        return new PanierViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PanierViewHolder holder, int position) {
        Panier panier = paniers.get(position);
        holder.tvCommerceName.setText(panier.getCommerceName());
        holder.tvDescription.setText(panier.getDescription());
        holder.tvPrice.setText(String.format(Locale.getDefault(), "Price: %.2f", panier.getPrice()));
        holder.tvQuantity.setText(String.format(Locale.getDefault(), "Quantity: %d", panier.getQuantity()));

        boolean canReserve = panier.isAvailable() && panier.getQuantity() > 0;
        holder.btnReserve.setEnabled(canReserve);
        holder.btnReserve.setOnClickListener(v -> onReserveClickListener.onReserveClicked(panier));
    }

    @Override
    public int getItemCount() {
        return paniers.size();
    }

    public static class PanierViewHolder extends RecyclerView.ViewHolder {
        TextView tvCommerceName;
        TextView tvDescription;
        TextView tvPrice;
        TextView tvQuantity;
        Button btnReserve;

        public PanierViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCommerceName = itemView.findViewById(R.id.tvCommerceName);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            btnReserve = itemView.findViewById(R.id.btnReserve);
        }
    }
}
