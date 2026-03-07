package com.example.cloudapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cloudapp.data.PanierRepository;
import com.google.firebase.auth.FirebaseAuthException;

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "RegisterActivity";

    private EditText etName;
    private EditText etEmail;
    private EditText etPassword;
    private Button btnCreateAccount;
    private PanierRepository panierRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        panierRepository = new PanierRepository();

        btnCreateAccount.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Name, email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreateAccount.setEnabled(false);
        FirebaseManager.getAuth().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    if (authResult.getUser() == null) {
                        btnCreateAccount.setEnabled(true);
                        Toast.makeText(this, "Registration failed: missing user", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = authResult.getUser().getUid();
                    panierRepository.createUserProfile(uid, name, email, new PanierRepository.UserCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(RegisterActivity.this, "Account created", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, HomeActivity.class));
                            finish();
                        }

                        @Override
                        public void onError(Exception e) {
                            btnCreateAccount.setEnabled(true);
                            Toast.makeText(
                                    RegisterActivity.this,
                                    "Profile save failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    btnCreateAccount.setEnabled(true);
                    String detail = e.getMessage();
                    if (e instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) e).getErrorCode();
                        detail = code + " - " + e.getMessage();
                    }
                    Log.e(TAG, "Registration failed", e);
                    Toast.makeText(this, "Registration failed: " + detail, Toast.LENGTH_LONG).show();
                });
    }
}
