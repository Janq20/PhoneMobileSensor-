package com.example.mobilesensor;

// =================================================================
// IMPORTY
// =================================================================
import android.Manifest;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface; // Dodane do pogrubienia czcionki
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity; // Dodane do centrowania
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // =================================================================
    // KONFIGURACJA
    // =================================================================
    private static final String API_KEY = "73388daab4f30826e3f8cca01c2ddb04";
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=pl";
    private static final int REQUEST_CODE_GPS_PERMISSION = 100;

    private static final int EKRAN_OGOLNE = 1;
    private static final int EKRAN_GPS = 2;
    private static final int EKRAN_ZYROSKOP = 3;
    private static final int EKRAN_SYSTEM = 4;
    private static final int EKRAN_APLIKACJA = 5;

    // =================================================================
    // ZMIENNE
    // =================================================================
    private TextView opisParametrow;
    private int aktualnieWybranyEkran = EKRAN_OGOLNE;

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private Sensor gyroscopeSensor;
    private Sensor accelerometerSensor;
    private float aktualneSwiatloLx = -1.0f;
    private final float[] aktualnyZyroskop = {0, 0, 0};
    private final float[] aktualnyAkcelerometr = {0, 0, 0};

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Geocoder geocoder;
    private double aktualnaSzerokosc = 0.0;
    private double aktualnaDlugosc = 0.0;
    private float aktualnaDokladnosc = 0.0f;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupUI();
        setupSensors();
        setupLocation();
        setupButtons();

        wyswietlInformacjeOgolne();
        sprawdzIpoprosOPermISjeGPS();
    }

    // --- KONFIGURACJA UI (TUTAJ ZMIENIAMY WIELKOŚĆ TEKSTU) ---
    private void setupUI() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        getWindow().setStatusBarColor(android.graphics.Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        opisParametrow = findViewById(R.id.opis_parametrow);

        // === TUTAJ JEST ZMIANA WYGLĄDU ===
        opisParametrow.setTextSize(20); // Zwiększenie czcionki
        opisParametrow.setTypeface(null, Typeface.BOLD); // Pogrubienie tekstu
        opisParametrow.setGravity(Gravity.CENTER); // Wyśrodkowanie tekstu
        opisParametrow.setPadding(40, 40, 40, 40); // Marginesy wewnętrzne
        // =================================

        opisParametrow.setOnClickListener(v -> {
            if (aktualnieWybranyEkran == EKRAN_GPS) {
                if (aktualnaSzerokosc != 0.0 && aktualnaDlugosc != 0.0) {
                    otworzMapyGoogle();
                } else {
                    Toast.makeText(this, "Brak współrzędnych GPS. Czekam na sygnał...", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void setupLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        geocoder = new Geocoder(this, Locale.getDefault());

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                aktualnaSzerokosc = location.getLatitude();
                aktualnaDlugosc = location.getLongitude();
                aktualnaDokladnosc = location.getAccuracy();

                if (aktualnieWybranyEkran == EKRAN_GPS) {
                    wyswietlInformacjeGPS();
                    pobierzDanePogodowe(aktualnaSzerokosc, aktualnaDlugosc);
                }
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override
            public void onProviderDisabled(@NonNull String provider) {
                aktualnaSzerokosc = 0.0;
                aktualnaDlugosc = 0.0;
                if (aktualnieWybranyEkran == EKRAN_GPS) wyswietlInformacjeGPS();
            }
        };
    }

    private void setupButtons() {
        findViewById(R.id.btn_ogolne).setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_OGOLNE;
            zatrzymajNasluchiwanieGPS();
            wyswietlInformacjeOgolne();
        });

        findViewById(R.id.btn_gps).setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_GPS;
            wyswietlInformacjeGPS();
            sprawdzIpoprosOPermISjeGPS();
        });

        findViewById(R.id.btn_zyroskop).setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_ZYROSKOP;
            zatrzymajNasluchiwanieGPS();
            wyswietlZyroskop();
        });

        findViewById(R.id.btn_system).setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_SYSTEM;
            zatrzymajNasluchiwanieGPS();
            wyswietlInformacjeSystemowe();
        });

        findViewById(R.id.btn_aplikacja).setOnClickListener(v -> {
            aktualnieWybranyEkran = EKRAN_APLIKACJA;
            zatrzymajNasluchiwanieGPS();
            opisParametrow.setText("DANE APLIKACJI\n-----------------------------------\n\n" +
                    "• Wersja: 1.1 UI Fix\n• Status: Aktywna");
        });
    }

    private void otworzMapyGoogle() {
        try {
            String uri = String.format(Locale.US, "geo:%f,%f?q=%f,%f(Tu jesteś)",
                    aktualnaSzerokosc, aktualnaDlugosc, aktualnaSzerokosc, aktualnaDlugosc);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            try {
                String url = String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%f,%f",
                        aktualnaSzerokosc, aktualnaDlugosc);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Nie znaleziono aplikacji map.", Toast.LENGTH_LONG).show();
            }
        }
    }
    //Funkcja Pokazywania pogody
    private void pobierzDanePogodowe(double lat, double lon) {
        executorService.execute(() -> {
            String response = "";
            try {
                String urlString = String.format(Locale.US, WEATHER_URL, lat, lon, API_KEY);
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder content = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();
                    response = content.toString();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            String finalResponse = response;
            mainHandler.post(() -> {
                if (aktualnieWybranyEkran == EKRAN_GPS && !finalResponse.isEmpty()) {
                    sformatujIWyswietlPogode(finalResponse);
                }
            });
        });
    }

    private void sformatujIWyswietlPogode(String jsonResponse) {
        try {
            JSONObject json = new JSONObject(jsonResponse);
            String miasto = json.optString("name", "Nieznane");
            JSONObject main = json.getJSONObject("main");
            JSONObject weather = json.getJSONArray("weather").getJSONObject(0);
            JSONObject wind = json.getJSONObject("wind");

            String opis = weather.optString("description", "");
            opis = opis.substring(0, 1).toUpperCase() + opis.substring(1);

            String tekstPogody = String.format(Locale.getDefault(),
                    "\n\n" +
                            "───── POGODA: %s ────\n" +
                            "🌡️  Temp: %.1f°C (Odczuwalna: %.1f°C)\n" +
                            "☁️  Niebo: %s\n" +
                            "------------------------------------\n" +
                            "💧  Wilgotność:   %d%%\n" +
                            "⏱️  Ciśnienie:    %d hPa\n" +
                            "💨  Wiatr:        %.1f m/s",
                    miasto.toUpperCase(),
                    main.getDouble("temp"),
                    main.getDouble("feels_like"),
                    opis,
                    main.getInt("humidity"),
                    main.getInt("pressure"),
                    wind.getDouble("speed"));

            String obecnyTekst = opisParametrow.getText().toString();
            String separator = "──────── POGODA";
            if (obecnyTekst.contains(separator)) {
                obecnyTekst = obecnyTekst.substring(0, obecnyTekst.indexOf("\n\n" + separator));
            }
            opisParametrow.setText(obecnyTekst + tekstPogody);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void wyswietlInformacjeOgolne() {
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memInfo);
        double totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0);

        String batteryStatusStr = "Ładowanie...";
        Intent batteryStatus = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryStatusStr = (int) ((level / (float) scale) * 100) + "%";
        }

        String infoText = String.format(Locale.getDefault(),
                "INFORMACJE O URZĄDZENIU\n" +
                        "-----------------------------------\n" +
                        "• Model: %s\n" +
                        "• Producent: %s\n\n" +
                        "PAMIĘĆ I BATERIA\n" +
                        "-----------------------------------\n" +
                        "• RAM: %.2f GB\n" +
                        "• Bateria: %s",
                Build.MODEL, Build.MANUFACTURER, totalRamGB, batteryStatusStr);
        opisParametrow.setText(infoText);
    }

    private void wyswietlInformacjeGPS() {
        StringBuilder sb = new StringBuilder("PARAMETRY GPS\n-----------------------------------\n\n");

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            sb.append("🔴 Brak uprawnień GPS.\nKliknij przycisk ponownie, aby zezwolić.");
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            sb.append("🔴 GPS jest wyłączony w ustawieniach.");
        } else if (aktualnaSzerokosc == 0.0) {
            sb.append("🟡 Szukanie satelitów...\n(Wyjdź na zewnątrz)");
        } else {
            sb.append("• Adres: ").append(pobierzAdres(aktualnaSzerokosc, aktualnaDlugosc)).append("\n");
            sb.append(String.format(Locale.US, "• Szerokość: %.6f\n", aktualnaSzerokosc));
            sb.append(String.format(Locale.US, "• Długość:   %.6f\n", aktualnaDlugosc));
            sb.append(String.format(Locale.US, "• Dokładność: %.1f m\n", aktualnaDokladnosc));
            sb.append("\n🗺️ KLIKNIJ TUTAJ, ABY OTWORZYĆ MAPĘ");
        }
        opisParametrow.setText(sb.toString());
    }

    private void wyswietlZyroskop() {
        String info = String.format(Locale.US,
                "ŻYROSKOP (Rad/s)\n-----------------------------------\n" +
                        "X: %.2f\nY: %.2f\nZ: %.2f\n\n" +
                        "AKCELEROMETR (m/s²)\n-----------------------------------\n" +
                        "X: %.2f\nY: %.2f\nZ: %.2f",
                aktualnyZyroskop[0], aktualnyZyroskop[1], aktualnyZyroskop[2],
                aktualnyAkcelerometr[0], aktualnyAkcelerometr[1], aktualnyAkcelerometr[2]);
        opisParametrow.setText(info);
    }

    private void wyswietlInformacjeSystemowe() {
        String info = String.format(Locale.US,
                "INFORMACJE SYSTEMOWE\n-----------------------------------\n\n" +
                        "• Światło: %.1f lx\n" +
                        "• Android: %s (API %d)\n" +
                        "• CPU: %s",
                aktualneSwiatloLx, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS[0]);
        opisParametrow.setText(info);
    }

    private String pobierzAdres(double lat, double lon) {
        if (!Geocoder.isPresent()) return "Geokodowanie niedostępne";
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String loc = addr.getLocality() != null ? addr.getLocality() : "";
                String th = addr.getThoroughfare() != null ? addr.getThoroughfare() : "";
                return (loc + " " + th).trim();
            }
        } catch (IOException e) {
            return "Błąd pobierania adresu";
        }
        return "Adres nieznany";
    }

    private void sprawdzIpoprosOPermISjeGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            uruchomNasluchiwanieGPS();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_GPS_PERMISSION);
        }
    }

    private void uruchomNasluchiwanieGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 5, locationListener);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, locationListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void zatrzymajNasluchiwanieGPS() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_GPS_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                uruchomNasluchiwanieGPS();
                if (aktualnieWybranyEkran == EKRAN_GPS) wyswietlInformacjeGPS();
            } else {
                Toast.makeText(this, "Brak zgody na GPS - funkcje lokalizacji ograniczone", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LIGHT:
                aktualneSwiatloLx = event.values[0];
                dostosujJasnoscEkranu(aktualneSwiatloLx);
                if (aktualnieWybranyEkran == EKRAN_SYSTEM) wyswietlInformacjeSystemowe();
                break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, aktualnyZyroskop, 0, 3);
                if (aktualnieWybranyEkran == EKRAN_ZYROSKOP) wyswietlZyroskop();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, aktualnyAkcelerometr, 0, 3);
                if (aktualnieWybranyEkran == EKRAN_ZYROSKOP) wyswietlZyroskop();
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void dostosujJasnoscEkranu(float lux) {
        float jasnosc = Math.min(Math.max(20.0f, lux), 1000.0f) / 1000.0f;
        if (jasnosc < 0.1f) jasnosc = 0.1f;

        WindowManager.LayoutParams layout = getWindow().getAttributes();
        layout.screenBrightness = jasnosc;
        getWindow().setAttributes(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (gyroscopeSensor != null) sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
            if (accelerometerSensor != null) sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
        }
        if (aktualnieWybranyEkran == EKRAN_GPS) {
            uruchomNasluchiwanieGPS();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        zatrzymajNasluchiwanieGPS();
    }
}