package com.example.mobilesensor;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent; // <-- NOWY IMPORT
import android.net.Uri; // <-- NOWY IMPORT
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private TextView opisParametrow;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float aktualneSwiatloLx = -1.0f;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private double aktualnaSzerokosc = 0.0;
    private double aktualnaDlugosc = 0.0;
    private float aktualnaDokladnosc = 0.0f;
    private static final int REQUEST_CODE_GPS_PERMISSION = 100;

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
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        opisParametrow = findViewById(R.id.opis_parametrow);
        opisParametrow.setOnClickListener(v -> {
            if (aktualnieWybranyEkran == EKRAN_GPS && aktualnaSzerokosc != 0.0) {

                String label = "Mapy";
                String uri = String.format(Locale.US, "geo:0,0?q=%f,%f(%s)",
                        aktualnaSzerokosc, aktualnaDlugosc, label);

                Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                mapIntent.setPackage("com.google.android.apps.maps");

                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                }
            }
        });
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if (lightSensor == null) {
            aktualneSwiatloLx = -1.0f;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        stworzLocationListener();

        Button btnOgolne = findViewById(R.id.btn_ogolne);
        Button btnGps = findViewById(R.id.btn_gps);
        Button btnZyroskop = findViewById(R.id.btn_zyroskop);
        Button btnSystem = findViewById(R.id.btn_system);
        Button btnAplikacja = findViewById(R.id.btn_aplikacja);

        btnOgolne.performClick();

        btnOgolne.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_OGOLNE;
            zatrzymajNasluchiwanieGPS();
            String model = Build.MODEL;
            String producent = Build.MANUFACTURER;

            ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
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
            wyswietlInformacjeGPS();
            sprawdzIpoprosOPermISjeGPS();
        });

        btnZyroskop.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_ZYROSKOP;
            zatrzymajNasluchiwanieGPS();
            opisParametrow.setText("Parametry żyroskopu:\n• Oś X\n• Oś Y\n• Oś Z");
        });

        btnSystem.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_SYSTEM;
            zatrzymajNasluchiwanieGPS();
            wyswietlInformacjeSystemowe();
        });

        btnAplikacja.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_APLIKACJA;
            zatrzymajNasluchiwanieGPS();
            opisParametrow.setText("Dane aplikacji:\n• Wersja\n• Uprawnienia\n• Zużycie energii");
        });
    }

    //===============================================================
    // FUNKCJE DO OBSŁUGI GPS
    //===============================================================

    private void stworzLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                aktualnaSzerokosc = location.getLatitude();
                aktualnaDlugosc = location.getLongitude();
                aktualnaDokladnosc = location.getAccuracy();

                if (aktualnieWybranyEkran == EKRAN_GPS) {
                    wyswietlInformacjeGPS();
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {
                aktualnaSzerokosc = 0.0;
                aktualnaDlugosc = 0.0;
                aktualnaDokladnosc = 0.0f;
                if (aktualnieWybranyEkran == EKRAN_GPS) {
                    wyswietlInformacjeGPS();
                }
            }
        };
    }

    private void wyswietlInformacjeGPS() {
        String infoGps = "Parametry GPS:\n\n";
        if (aktualnaSzerokosc == 0.0 && aktualnaDlugosc == 0.0) {

            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                infoGps += "BŁĄD: Lokalizacja (GPS) jest wyłączona w ustawieniach telefonu.";
            } else {
                infoGps += "Oczekiwanie na sygnał GPS...\n(Upewnij się, że masz widok na niebo)";
            }

        } else {
            infoGps += String.format(Locale.US, "• Szerokość geogr.: %.6f\n", aktualnaSzerokosc);
            infoGps += String.format(Locale.US, "• Długość geogr.: %.6f\n", aktualnaDlugosc);
            infoGps += String.format(Locale.US, "• Dokładność: %.1f m\n", aktualnaDokladnosc);
            infoGps += "\n(Kliknij, aby zobaczyć na mapie)"; // <-- DODANA LINIA
        }
        opisParametrow.setText(infoGps);
    }

    private void sprawdzIpoprosOPermISjeGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            uruchomNasluchiwanieGPS();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_GPS_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_GPS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                uruchomNasluchiwanieGPS();
            } else {
                opisParametrow.setText("Parametry GPS:\n\nNie udzielono zgody na dostęp do lokalizacji. Funkcja niedostępna.");
            }
        }
    }

    private void uruchomNasluchiwanieGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            // Używamy NETWORK_PROVIDER dla szybszego fixa (testy w budynku)
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        1000,
                        1,
                        locationListener);
            }
            // Używamy też GPS_PROVIDER dla dokładności (działa na zewnątrz)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        1000,
                        1,
                        locationListener);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
            opisParametrow.setText("Błąd SecurityException przy uruchamianiu GPS.");
        }
    }

    private void zatrzymajNasluchiwanieGPS() {
        locationManager.removeUpdates(locationListener);
    }


    //===============================================================
    // FUNKCJA SYSTEMU (CZUJNIK ŚWIATŁA)
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
        opisParametrow.setText(infoSystem);
    }

    //=================================================
    // ZARZĄDZANIE CYKLEM ŻYCIA (onResume / onPause)
    //=================================================

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (aktualnieWybranyEkran == EKRAN_GPS) {
            sprawdzIpoprosOPermISjeGPS();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        zatrzymajNasluchiwanieGPS();
    }

    //=================================================
    // FUNKCJE CZUJNIKÓW (onSensorChanged)
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
    // FUNKCJA ZMIANY JASNOŚCI EKRANU
    //=================================================

    private void zmienJasnosAplikacji(float lux) {
        final float MAX_LUX = 40000.0f;
        final float MIN_LUX = 1.0f;
        final float MIN_BRIGHTNESS = 0.01f;
        final float MAX_BRIGHTNESS = 1.0f;

        float clampedLux = Math.min(Math.max(MIN_LUX, lux), MAX_LUX);
        double logLux = Math.log(clampedLux);
        double logMin = Math.log(MIN_LUX);
        double logMax = Math.log(MAX_LUX);

        float normalizedBrightness = (float) ((logLux - logMin) / (logMax - logMin));
        float jasnosc = MIN_BRIGHTNESS + (normalizedBrightness * (MAX_BRIGHTNESS - MIN_BRIGHTNESS));

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = jasnosc;
        getWindow().setAttributes(layout);
    }
}