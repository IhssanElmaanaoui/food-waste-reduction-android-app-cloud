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

import com.example.cloudapp.data.PanierRepository;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText etEmail;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnGoToRegister;
    private PanierRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
        repository = new PanierRepository();

        // Toujours afficher la page connexion au démarrage (pas de redirection auto si déjà connecté)
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnLogin.setOnClickListener(v -> login());
        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Réactiver le bouton Login au retour (il est désactivé pendant la tentative de connexion)
        if (btnLogin != null) {
            btnLogin.setEnabled(true);
        }
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
                .addOnSuccessListener(authResult -> goToRoleHome())
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

    private void goToRoleHome() {
        repository.getCurrentUserRole(new PanierRepository.RoleCallback() {
            @Override
            public void onSuccess(String role, boolean approved) {
                FcmTokenManager.syncCurrentUserToken();
                if ("merchant".equalsIgnoreCase(role) && !approved) {
                    btnLogin.setEnabled(true);
                    Toast.makeText(
                            LoginActivity.this,
                            "Compte commerçant en attente de validation par un admin. Connectez-vous en tant qu'admin pour valider.",
                            Toast.LENGTH_LONG
                    ).show();
                    return;
                }
                Class<?> target = HomeActivity.class;
                if ("admin".equalsIgnoreCase(role)) {
                    target = AdminActivity.class;
                } else if ("merchant".equalsIgnoreCase(role)) {
                    target = MerchantActivity.class;
                }
                startActivity(new Intent(LoginActivity.this, target));
                // Ne pas finish() pour que le retour depuis Home/Admin/Merchant revienne ici (pas vers une autre app)
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Role fetch failed", e);
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
            }
        });
    }
}
