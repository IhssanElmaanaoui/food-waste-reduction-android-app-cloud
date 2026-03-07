package com.example.cloudapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuthException;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText etEmail;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnGoToRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);

        if (FirebaseManager.getAuth().getCurrentUser() != null) {
            goToHome();
            return;
        }

        btnLogin.setOnClickListener(v -> login());
        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        FirebaseManager.getAuth()
                .signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> goToHome())
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    String detail = e.getMessage();
                    if (e instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) e).getErrorCode();
                        detail = code + " - " + e.getMessage();
                    }
                    Log.e(TAG, "Login failed", e);
                    Toast.makeText(this, "Login failed: " + detail, Toast.LENGTH_LONG).show();
                });
    }

    private void goToHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
