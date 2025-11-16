package com.example.mobilesensor;

// =================================================================
// IMPORTS - BIBLIOTEKI ANDROIDX / CORE
// =================================================================
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

// =================================================================
// IMPORTS - GPS / LOKALIZACJA
// =================================================================
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.Address;
import android.location.Geocoder;
import java.io.IOException;
import java.util.List;

// =================================================================
// IMPORTS - SYSTEM / SENSORY / BATERIA
// =================================================================
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

// =================================================================
// IMPORTS - JĘZYKOWE / NARZĘDZIOWE
// =================================================================
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // =================================================================
    // ZMIENNE WIDOKÓW / GŁÓWNE
    // =================================================================
    private TextView opisParametrow;
    private int aktualnieWybranyEkran = 0;
    private static final int EKRAN_OGOLNE = 1;
    private static final int EKRAN_GPS = 2;
    private static final int EKRAN_ZYROSKOP = 3;
    private static final int EKRAN_SYSTEM = 4;
    private static final int EKRAN_APLIKACJA = 5;

    // =================================================================
    // ZMIENNE SENSORÓW / SYSTEMU
    // =================================================================
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float aktualneSwiatloLx = -1.0f;
    private Sensor gyroscopeSensor;
    private final float[] aktualnyZyroskop = {0, 0, 0};

    // =================================================================
    // ZMIENNE GPS / LOKALIZACJI
    // =================================================================
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Geocoder geocoder;
    private double aktualnaSzerokosc = 0.0;
    private double aktualnaDlugosc = 0.0;
    private float aktualnaDokladnosc = 0.0f;
    private static final int REQUEST_CODE_GPS_PERMISSION = 100;

    // =================================================================
    // GŁÓWNA KONFIGURACJA APLIKACJI (onCreate)
    // =================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
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

        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (lightSensor == null) {
            aktualneSwiatloLx = -1.0f;
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        stworzLocationListener();
        geocoder = new Geocoder(this, Locale.getDefault());

        Button btnOgolne = findViewById(R.id.btn_ogolne);
        Button btnGps = findViewById(R.id.btn_gps);
        Button btnZyroskop = findViewById(R.id.btn_zyroskop);
        Button btnSystem = findViewById(R.id.btn_system);
        Button btnAplikacja = findViewById(R.id.btn_aplikacja);

        btnOgolne.performClick();

        // =================================================================
        // OBSŁUGA KLIKNIĘĆ PRZYCISKÓW
        // =================================================================
        btnOgolne.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_OGOLNE;
            zatrzymajNasluchiwanieGPS();

            String model = Build.MODEL;
            String producent = Build.MANUFACTURER;

            ActivityManager actManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            actManager.getMemoryInfo(memInfo);
            double totalRamInGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0);
            String totalRamStr = String.format(Locale.US, "%.2f GB", totalRamInGB);
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            String batteryStatusStr = "Nieznany";
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level != -1 && scale != -1) {
                    float batteryPct = level / (float)scale * 100;
                    batteryStatusStr = String.format(Locale.US, "%.0f%%", batteryPct);
                }
            }
            String infoText = String.format(
                    "INFORMACJE O URZĄDZENIU\n" +
                            "-----------------------------------\n" +
                            "• Model: %s\n" +
                            "• Producent: %s\n\n" +
                            "PAMIĘĆ I BATERIA\n" +
                            "-----------------------------------\n" +
                            "• Całkowita pamięć RAM: %s\n" +
                            "• Poziom baterii: %s",
                    model, producent, totalRamStr, batteryStatusStr
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
            wyswietlZyroskop();
        });

        btnSystem.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_SYSTEM;
            zatrzymajNasluchiwanieGPS();
            wyswietlInformacjeSystemowe();
        });

        btnAplikacja.setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_APLIKACJA;
            zatrzymajNasluchiwanieGPS();
            opisParametrow.setText("DANE APLIKACJI\n-----------------------------------\n\n" +
                    "• Wersja\n• Uprawnienia\n• Zużycie energii");
        });
    }

    // =================================================================
    // FUNKCJE OBSŁUGI GPS
    // =================================================================
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
            public void onProviderEnabled(String provider) {
                if (aktualnieWybranyEkran == EKRAN_GPS) {
                    wyswietlInformacjeGPS();
                }
            }
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
        String infoGps = "PARAMETRY GPS\n-----------------------------------\n\n";

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            infoGps += "Oczekiwanie na zgodę użytkownika...";
        }
        else if (aktualnaSzerokosc == 0.0 && aktualnaDlugosc == 0.0) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                infoGps += "BŁĄD: Lokalizacja (GPS) jest wyłączona w ustawieniach telefonu.";
            } else {
                infoGps += "Oczekiwanie na sygnał GPS...\n(Upewnij się, że masz widok na niebo)";
            }
        } else {
            String adres = pobierzAdres(aktualnaSzerokosc, aktualnaDlugosc);
            infoGps += "• Lokalizacja: " + adres + "\n";
            infoGps += String.format(Locale.US, "• Szerokość geogr.: %.6f\n", aktualnaSzerokosc);
            infoGps += String.format(Locale.US, "• Długość geogr.: %.6f\n", aktualnaDlugosc);
            infoGps += String.format(Locale.US, "• Dokładność: %.1f m\n", aktualnaDokladnosc);
            infoGps += "\n(Kliknij, aby zobaczyć na mapie)";
        }
        opisParametrow.setText(infoGps);
    }

    private String pobierzAdres(double lat, double lon) {
        if (geocoder == null || !Geocoder.isPresent()) {
            return "Wyszukiwanie adresów niedostępne";
        }
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address returnedAddress = addresses.get(0);
                StringBuilder strReturnedAddress = new StringBuilder();
                if (returnedAddress.getLocality() != null) {
                    strReturnedAddress.append(returnedAddress.getLocality()).append(", ");
                }
                if (returnedAddress.getThoroughfare() != null) {
                    strReturnedAddress.append(returnedAddress.getThoroughfare());
                } else if (returnedAddress.getFeatureName() != null) {
                    strReturnedAddress.append(returnedAddress.getFeatureName());
                }

                String finalAddress = strReturnedAddress.toString().trim();
                if (finalAddress.endsWith(",")) {
                    finalAddress = finalAddress.substring(0, finalAddress.length() - 1);
                }
                return finalAddress.isEmpty() ? "Adres nieznany" : finalAddress;

            } else {
                return "Brak adresu";
            }
        } catch (IOException e) {
            return "Błąd Geocodera";
        } catch (IllegalArgumentException e) {
            return "Nieprawidłowe współrzędne";
        }
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
                wyswietlInformacjeGPS();
            } else {
                opisParametrow.setText("PARAMETRY GPS\n-----------------------------------\n\n" +
                        "Nie udzielono zgody na dostęp do lokalizacji. Funkcja niedostępna.");
            }
        }
    }

    private void uruchomNasluchiwanieGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        1000,
                        1,
                        locationListener);
            }
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

    // =================================================================
    // FUNKCJE OBSŁUGI SENSORÓW
    // =================================================================
    private void wyswietlZyroskop() {
        String infoZyroskop;
        if (gyroscopeSensor == null) {
            infoZyroskop = "PARAMETRY ŻYROSKOPU\n-----------------------------------\n\n" +
                    "Czujnik żyroskopu jest niedostępny na tym urządzeniu.";
        } else {
            infoZyroskop = String.format(Locale.US,
                    "PARAMETRY ŻYROSKOPU\n" +
                            "-----------------------------------\n" +
                            "(prędkość kątowa)\n\n" +
                            "• Oś X (pochylenie): %.1f rad/s\n" +
                            "• Oś Y (przechylenie): %.1f rad/s\n" +
                            "• Oś Z (obrót): %.1f rad/s",
                    aktualnyZyroskop[0], aktualnyZyroskop[1], aktualnyZyroskop[2]
            );
        }
        opisParametrow.setText(infoZyroskop);
    }


    private void wyswietlInformacjeSystemowe() {
        String infoSystem = "INFORMACJE SYSTEMOWE\n-----------------------------------\n\n";

        if (aktualneSwiatloLx != -1.0f) {
            infoSystem += String.format(Locale.US, "• Aktualne światło: %.2f lx\n", aktualneSwiatloLx);
        } else {
            infoSystem += "• Czujnik światła: Niedostępny\n";
        }

        infoSystem += "\nINNE PARAMETRY\n-----------------------------------\n";
        infoSystem += "• Wersja Androida: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n";

        String cpuAbi = "Niedostępne";
        if (Build.SUPPORTED_ABIS.length > 0) {
            cpuAbi = Build.SUPPORTED_ABIS[0];
        }
        infoSystem += "• Architektura CPU: " + cpuAbi + "\n";

        opisParametrow.setText(infoSystem);
    }

    // =================================================================
    // ZARZĄDZANIE CYKLEM ŻYCIA (onResume / onPause)
    // =================================================================
    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
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

    // =================================================================
    // FUNKCJE CZUJNIKÓW (onSensorChanged / onAccuracyChanged)
    // =================================================================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            aktualneSwiatloLx = event.values[0];
            zmienJasnosAplikacji(aktualneSwiatloLx);

            if (aktualnieWybranyEkran == EKRAN_SYSTEM) {
                wyswietlInformacjeSystemowe();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            aktualnyZyroskop[0] = event.values[0];
            aktualnyZyroskop[1] = event.values[1];
            aktualnyZyroskop[2] = event.values[2];

            if (aktualnieWybranyEkran == EKRAN_ZYROSKOP) {
                wyswietlZyroskop();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // =================================================================
    // FUNKCJA DODATKOWA (Jasność)
    // =================================================================
    private void zmienJasnosAplikacji(float lux) {
        final float MIN_LUX = 20.0f;
        final float MAX_LUX = 1000.0f;
        final float MIN_BRIGHTNESS = 0.1f;
        final float MAX_BRIGHTNESS = 1.0f;

        float clampedLux = Math.min(Math.max(MIN_LUX, lux), MAX_LUX);
        float normalizedBrightness = (clampedLux - MIN_LUX) / (MAX_LUX - MIN_LUX);
        float jasnosc = MIN_BRIGHTNESS + (normalizedBrightness * (MAX_BRIGHTNESS - MIN_BRIGHTNESS));

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = jasnosc;
        getWindow().setAttributes(layout);
    }
}