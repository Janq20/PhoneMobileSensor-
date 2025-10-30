package com.example.mobilesensor;

import android.app.ActivityManager; // <-- DODANY IMPORT do informacji o RAM
import android.content.Context;          // Udostępnia informacje o środowisku aplikacji i dostęp do usług systemowych.
import android.hardware.Sensor;          // Reprezentuje fizyczny czujnik (np. światła, akcelerometr) na urządzeniu.
import android.hardware.SensorEvent;     // Obiekt przechowujący dane (np. wartości pomiarowe) wygenerowane przez czujnik.
import android.hardware.SensorEventListener; // Interfejs do odbierania powiadomień o zmianach danych z czujnika.
import android.hardware.SensorManager;     // Klasa do zarządzania czujnikami: ich listowania, rejestrowania i wyrejestrowywania.
import android.os.Build;                // Zawiera stałe i metody dostarczające informacji o bieżącej wersji systemu Android.
import android.os.Bundle;              // Obiekt używany do przekazywania danych (np. stanu instancji) między komponentami aplikacji.
import android.view.WindowManager;      // Interfejs do zarządzania oknami aplikacji, w tym ich atrybutami (np. jasnością).
import android.widget.Button;            // Komponent interfejsu użytkownika reprezentujący klikalny przycisk.
import android.widget.TextView;          // Komponent interfejsu użytkownika służący do wyświetlania tekstu.
import androidx.appcompat.app.AppCompatActivity; // Bazowa klasa Activity zapewniająca zgodność funkcji (np. paska akcji) ze starszymi wersjami Androida.

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView opisParametrow;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float aktualneSwiatloLx = -1.0f;

    private int aktualnieWybranyEkran = 0;
    private static final int EKRAN_OGOLNE = 1;
    private static final int EKRAN_GPS = 2;
    private static final int EKRAN_ZYROSKOP = 3;
    private static final int EKRAN_SYSTEM = 4;
    private static final int EKRAN_APLIKACJA = 5;

    //=================================================
    // GŁÓWNA KONFIGURACJA APLIKACJI (onCreate)
    //=================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        opisParametrow = findViewById(R.id.opis_parametrow);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) {
            aktualneSwiatloLx = -1.0f;
        }

        Button btnOgolne = findViewById(R.id.btn_ogolne);
        Button btnGps = findViewById(R.id.btn_gps);
        Button btnZyroskop = findViewById(R.id.btn_zyroskop);
        Button btnSystem = findViewById(R.id.btn_system);
        Button btnAplikacja = findViewById(R.id.btn_aplikacja);

        btnOgolne.performClick();

        btnOgolne.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_OGOLNE;
            String model = Build.MODEL;
            String producent = Build.MANUFACTURER;

            // POBIERANIE INFORMACJI O PAMIĘCI TYMCZASOWEJ [RAM]
            ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
            // Konwersja z bajtów na Gigabajty (GB)
            double totalRamInGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0);
            String totalRamStr = String.format("%.2f GB", totalRamInGB);

            String infoText = String.format(
                    "Informacje o urządzeniu:\n\n" +
                            "• Model: %s\n" +
                            "• Producent: %s\n" +
                            "• Całkowita pamięć RAM: %s",
                    model, producent, totalRamStr
            );
            opisParametrow.setText(infoText);
        });

        btnGps.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_GPS;
            opisParametrow.setText("Parametry GPS:\n• Szerokość geograficzna\n• Długość geograficzna\n• Dokładność sygnału");
        });

        btnZyroskop.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_ZYROSKOP;
            opisParametrow.setText("Parametry żyroskopu:\n• Oś X\n• Oś Y\n• Oś Z");
        });

        btnSystem.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_SYSTEM;
            wyswietlInformacjeSystemowe();
        });

        btnAplikacja.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_APLIKACJA;
            opisParametrow.setText("Dane aplikacji:\n• Wersja\n• Uprawnienia\n• Zużycie energii");
        });
    }

    //===============================================================
    // FUNKCJA SYSTEMU CZYLI POKAZYWANIE PARAMETRÓW CZUJNIKA ŚWIATŁĄ
    //===============================================================

    private void wyswietlInformacjeSystemowe() {
        String infoSystem = "Informacje o systemie:\n\n";

        if (aktualneSwiatloLx != -1.0f) {
            infoSystem += String.format("• Aktualne światło: %.2f lx\n", aktualneSwiatloLx);
        } else {
            infoSystem += "• Czujnik światła: Niedostępny\n";
        }

        infoSystem += "\nInne parametry systemowe:\n";
        infoSystem += "• Wersja Androida: " + Build.VERSION.RELEASE + "\n";
        // infoSystem += "• Poziom API: " + Build.VERSION.SDK_INT + "\n"; // <-- USUNIĘTA LINIA

        opisParametrow.setText(infoSystem);
    }

    //=================================================
    // ZARZĄDZANIE CYKLEM ŻYCIA CZUJNIKA
    //=================================================

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    //=================================================
    // FUNKCJA DZIAŁANIA CZUJNIKA ŚWIATŁA (onSensorChanged)
    //=================================================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            aktualneSwiatloLx = event.values[0];
            zmienJasnosAplikacji(aktualneSwiatloLx);

            if (aktualnieWybranyEkran == EKRAN_SYSTEM) {
                wyswietlInformacjeSystemowe();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    //=================================================
    // FUNKCJA ZMIANY JASNOŚCI EKRANU [1 CZUJNIK ZROBIONY!] i nawet działa
    //=================================================

    private void zmienJasnosAplikacji(float lux) {
        float jasnosc;
        if (lux < 20) {
            jasnosc = 0.05f;
        } else if (lux > 5000) {
            jasnosc = 1.0f;
        } else {
            jasnosc = (lux / 5000.0f) * 0.95f + 0.05f;
        }

        jasnosc = Math.min(1.0f, Math.max(0.01f, jasnosc));

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = jasnosc;
        getWindow().setAttributes(layout);
    }
}