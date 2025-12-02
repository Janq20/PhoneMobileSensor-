package com.example.mobilesensor;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private TextView opisParametrow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        opisParametrow = findViewById(R.id.opis_parametrow);

        Button btnOgolne = findViewById(R.id.btn_ogolne);
        Button btnGps = findViewById(R.id.btn_gps);
        Button btnZyroskop = findViewById(R.id.btn_zyroskop);
        Button btnSystem = findViewById(R.id.btn_system);
        Button btnAplikacja = findViewById(R.id.btn_aplikacja);

        btnOgolne.setOnClickListener(v ->
                opisParametrow.setText("Informacje ogólne o urządzeniu:\n• Model telefonu\n• Producent\n• Wersja Androida")
        );

        btnGps.setOnClickListener(v ->
                opisParametrow.setText("Parametry GPS:\n• Szerokość geograficzna\n• Długość geograficzna\n• Dokładność sygnału")
        );

        btnZyroskop.setOnClickListener(v ->
                opisParametrow.setText("Parametry żyroskopu:\n• Oś X\n• Oś Y\n• Oś Z")
        );

        btnSystem.setOnClickListener(v ->
                opisParametrow.setText("Informacje o systemie:\n• Uptime\n• Wykorzystanie RAM\n• Obciążenie CPU")
        );

        btnAplikacja.setOnClickListener(v ->
                opisParametrow.setText("Dane aplikacji:\n• Wersja\n• Uprawnienia\n• Zużycie energii")
        );
    }
}
