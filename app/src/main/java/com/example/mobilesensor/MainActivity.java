package com.example.mobilesensor;

/**
 * @file MainActivity.java
 * @brief Główna aktywność aplikacji MobileSensor.
 *
 * @details
 * Aplikacja prezentuje informacje o urządzeniu (RAM, bateria, CPU, ekran),
 * dane GPS (współrzędne, dokładność, geokodowanie, pogoda z OpenWeather),
 * sensory (żyroskop, akcelerometr, czujnik światła), a także integruje wykresy
 * w czasie rzeczywistym (MPAndroidChart) oraz persystuje uśrednione próbki
 * do Firebase Realtime Database.
 *
 * Funkcje kluczowe:
 * - Pomiary i odświeżanie danych systemowych (RAM, bateria, temperatura, CPU).
 * - Nasłuchiwanie lokalizacji GPS/Network + geokodowanie adresu.
 * - Pobieranie pogody (OpenWeather) dla bieżących współrzędnych.
 * - Wyświetlanie wykresów (RAM, bateria, temp., światło, częstotliwość CPU).
 * - Obsługa latarki (torch) przy użyciu Camera2 API.
 * - Kopiowanie bieżącego opisu do schowka, wibracje dotykowe.
 * - Zapisywanie uśrednionych próbek do Firebase.
 *
 * Wymagane uprawnienia:
 * - ACCESS_FINE_LOCATION (GPS)
 * - INTERNET (pogoda/mapy)
 * - CAMERA (latarka)
 *
 * Zależności:
 * - Firebase Realtime Database
 * - MPAndroidChart
 *
 * @author
 * Janq20
 * @date 2025-12-05
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* ==== BIBLIOTEKI FIREBASE ==== */
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

/* ==== BIBLIOTEKI WYKRESÓW (MPAndroidChart) ==== */
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

/**
 * @class MainActivity
 * @brief Aktywność odpowiedzialna za prezentację i monitorowanie danych systemowych, GPS oraz sensorów.
 *
 * @implements SensorEventListener
 *
 * @section lifecycle Cykl życia
 * - onCreate: Inicjalizacja Firebase, UI, sensorów, lokalizacji, latarki i pętli odświeżania.
 * - onResume: Rejestracja nasłuchów sensorów i (warunkowo) GPS.
 * - onPause: Wyrejestrowanie sensorów, zatrzymanie GPS oraz odświeżania RAM, wyłączenie latarki.
 * - onDestroy: Zwolnienie zasobów (Firebase listener, ExecutorService).
 *
 * @section charts Wykresy
 * Wykorzystuje pięć wykresów liniowych:
 * - RAM (MB zużyte)
 * - Bateria (%)
 * - Temperatura baterii (°C)
 * - Światło (lx)
 * - Częstotliwość CPU (GHz)
 *
 * @section firebase Firebase
 * Zapisuje uśrednione próbki co RAM_PUBLISH_INTERVAL_MS do gałęzi "statystyki_urzadzen/{deviceId}".
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {

    /* ==== KONFIGURACJA STAŁYCH ==== */
    /** Klucz API OpenWeather. */
    private static final String API_KEY = "73388daab4f30826e3f8cca01c2ddb04";
    /** Szablon URL do pobierania pogody (metric, lang=pl). */
    private static final String WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&appid=%s&units=metric&lang=pl";
    /** URL bazy Firebase. */
    private static final String FIREBASE_URL = "https://mobilesensormonitor-default-rtdb.europe-west1.firebasedatabase.app";

    /** Kod żądania uprawnień GPS. */
    private static final int REQUEST_CODE_GPS_PERMISSION = 100;
    /** Kod żądania uprawnień kamery (latarka). */
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 101;

    // Ekrany
    /** Id ekranu ogólnego. */
    private static final int EKRAN_OGOLNE = 1;
    /** Id ekranu GPS. */
    private static final int EKRAN_GPS = 2;
    /** Id ekranu żyroskopu/akcelerometru. */
    private static final int EKRAN_ZYROSKOP = 3;
    /** Id ekranu systemowego. */
    private static final int EKRAN_SYSTEM = 4;
    /** Id ekranu informacji o aplikacji. */
    private static final int EKRAN_APLIKACJA = 5;

    /* ==== ELEMENTY UI ==== */
    /** Główny TextView z opisem aktualnych parametrów. */
    private TextView opisParametrow;
    /** Kontener na wykresy. */
    private LinearLayout layoutWykresy;

    // Wykresy
    /** Wykres użycia RAM. */
    private LineChart chartRam;
    /** Wykres poziomu baterii. */
    private LineChart chartBattery;
    /** Wykres temperatury baterii. */
    private LineChart chartTemp;
    /** Wykres natężenia światła (lx). */
    private LineChart chartLight;
    /** Wykres częstotliwości CPU (GHz). */
    private LineChart chartCpu;

    /** Aktualnie wybrany ekran. */
    private int aktualnieWybranyEkran = EKRAN_OGOLNE;

    /* ==== SENSORY I SPRZĘT ==== */
    /** Manager sensorów. */
    private SensorManager sensorManager;
    /** Czujnik światła. */
    private Sensor lightSensor;
    /** Czujnik żyroskopu. */
    private Sensor gyroscopeSensor;
    /** Czujnik akcelerometru. */
    private Sensor accelerometerSensor;
    /** Bieżąca wartość natężenia światła [lx]. */
    private float aktualneSwiatloLx = 0.0f;
    /** Ostatnie odczyty żyroskopu (X/Y/Z). */
    private final float[] aktualnyZyroskop = {0,0,0};
    /** Ostatnie odczyty akcelerometru (X/Y/Z). */
    private final float[] aktualnyAkcelerometr = {0,0,0};

    /** Manager lokalizacji. */
    private LocationManager locationManager;
    /** Nasłuch lokalizacji. */
    private LocationListener locationListener;
    /** Geocoder dla tłumaczenia współrzędnych na adres. */
    private Geocoder geocoder;
    /** Szerokość geograficzna. */
    private double aktualnaSzerokosc = 0.0;
    /** Długość geograficzna. */
    private double aktualnaDlugosc = 0.0;
    /** Dokładność lokalizacji [m]. */
    private float aktualnaDokladnosc = 0.0f;

    /** Manager kamery dla latarki. */
    private CameraManager cameraManager;
    /** Identyfikator kamery z lampą błyskową. */
    private String cameraId;
    /** Flaga informująca, czy latarka jest włączona. */
    private boolean isFlashlightOn = false;

    /* ==== WĄTKI I ODŚWIEŻANIE ==== */
    /** Jednowątkowy executor dla operacji sieciowych (np. pogoda). */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    /** Handler UI (główny wątek). */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Handler pętli odświeżania RAM/system. */
    private final Handler ramHandler = new Handler(Looper.getMainLooper());
    /** Runnable pętli odświeżania. */
    private Runnable ramRunnable;

    /** Maksymalna liczba punktów na wykresie lokalnym (rolling). */
    private static final int MAX_LOCAL_POINTS = 60;
    /** Interwał wysyłania uśrednionych danych do Firebase [ms]. */
    private static final long RAM_PUBLISH_INTERVAL_MS = 60_000;
    /** Znacznik czasu ostatniej publikacji. */
    private long lastPublishTs = 0;

    // Zmienne do uśredniania (Firebase)
    /** Suma wolnej pamięci RAM (MB) dla uśredniania. */
    private double sumRam = 0;
    /** Suma temperatury baterii (°C) dla uśredniania. */
    private double sumTemp = 0;
    /** Suma częstotliwości CPU (GHz) dla uśredniania. */
    private double sumCpu = 0;
    /** Liczba próbek zebranych od ostatniej publikacji. */
    private int countSamples = 0;

    /* ==== FIREBASE ==== */
    /** Czy połączenie z Firebase zostało zainicjalizowane. */
    private boolean firebasePolaczony = false;
    /** Referencja do gałęzi urządzenia w Firebase. */
    private DatabaseReference firebaseDeviceRef;
    /** Listener (opcjonalny) Firebase dla RAM. */
    private ValueEventListener ramFirebaseListener;
    /** Identyfikator urządzenia używany w Firebase. */
    private String deviceId;

    /**
     * @brief Metoda cyklu życia: inicjalizacja modułów i UI.
     * @param savedInstanceState stan zapisany (jeśli dostępny).
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirebase();
        setupUI();
        setupSensors();
        setupLocation();
        setupFlashlight();
        setupButtons();
        setupRamRefresher();
        startRamRefresher();
        sprawdzIpoprosOPermISjeGPS();
    }

    /* ==== INICJALIZACJA FIREBASE ==== */

    /**
     * @brief Inicjalizuje Firebase i przygotowuje referencję do gałęzi urządzenia.
     * @details Tworzy deviceId na podstawie modelu i fragmentu czasu, ustawia flagę połączenia.
     */
    private void initFirebase() {
        try {
            FirebaseApp.initializeApp(this);
            FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
            deviceId = Build.MODEL.replace(" ", "_") + "_" + (System.currentTimeMillis() % 10000);
            firebaseDeviceRef = db.getReference("statystyki_urzadzen").child(deviceId);
            firebasePolaczony = true;
        } catch (Exception e) {
            firebasePolaczony = false;
            Toast.makeText(this, "Błąd Firebase: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /* ==== CYKL ŻYCIA APLIKACJI ==== */

    /**
     * @brief Rejestruje nasłuchy sensorów oraz (warunkowo) GPS po wznowieniu aktywności.
     */
    @Override protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (lightSensor != null) sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (gyroscopeSensor != null) sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
            if (accelerometerSensor != null) sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (aktualnieWybranyEkran == EKRAN_GPS) uruchomNasluchiwanieGPS();
    }

    /**
     * @brief Wyrejestrowuje nasłuchy sensorów, zatrzymuje GPS i pętlę odświeżania oraz latarkę przy pauzie.
     */
    @Override protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        zatrzymajNasluchiwanieGPS();
        stopRamRefresher();
        if (isFlashlightOn) {
            try { cameraManager.setTorchMode(cameraId, false); isFlashlightOn = false; } catch (Exception ignored){}
        }
    }

    /**
     * @brief Zwalnia zasoby przy niszczeniu aktywności (handler, Firebase listener, executor).
     */
    @Override protected void onDestroy() {
        stopRamRefresher();
        stopFirebaseRamListener();
        executorService.shutdownNow();
        super.onDestroy();
    }

    /* ==== KONFIGURACJA INTERFEJSU (UI) ==== */

    /**
     * @brief Przygotowuje UI: ukrywa pasek akcji, konfiguruje status bar, binduje widoki i wykresy.
     * @details Ustawia style wykresów, obsługę kliknięć i długich kliknięć na polu opisu.
     */
    private void setupUI() {
        if (getSupportActionBar()!=null) getSupportActionBar().hide();
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        opisParametrow = findViewById(R.id.opis_parametrow);
        layoutWykresy = findViewById(R.id.layout_wykresy);

        // Przypisanie wykresów
        chartRam = findViewById(R.id.chart_ram);
        chartBattery = findViewById(R.id.chart_battery);
        chartTemp = findViewById(R.id.chart_temp);
        chartLight = findViewById(R.id.chart_light);
        chartCpu = findViewById(R.id.chart_cpu);

        // Konfiguracja stylów wykresów
        setupSingleChart(chartRam, Color.GREEN, 0, 0); // Auto-scale
        setupSingleChart(chartBattery, Color.YELLOW, 0, 100);
        setupSingleChart(chartTemp, Color.RED, 15, 50);
        setupSingleChart(chartLight, Color.CYAN, 0, 1000);
        setupSingleChart(chartCpu, Color.MAGENTA, 0, 3.5f);

        // Resetowanie autoscale
        if(chartLight != null && chartLight.getAxisLeft() != null) {
            chartLight.getAxisLeft().resetAxisMaximum();
            chartLight.getAxisLeft().resetAxisMinimum();
        }
        if(chartRam != null && chartRam.getAxisLeft() != null) chartRam.getAxisLeft().resetAxisMaximum();
        if(chartCpu != null && chartCpu.getAxisLeft() != null) chartCpu.getAxisLeft().resetAxisMaximum();

        opisParametrow.setOnClickListener(v -> {
            if (aktualnieWybranyEkran == EKRAN_GPS) {
                if (aktualnaSzerokosc != 0.0 && aktualnaDlugosc != 0.0) {
                    wibruj(50); otworzMapyGoogle();
                } else Toast.makeText(this,"Brak współrzędnych GPS.",Toast.LENGTH_SHORT).show();
            } else if (aktualnieWybranyEkran == EKRAN_SYSTEM) {
                wibruj(50); przelaczLatarke();
            }
        });
        opisParametrow.setOnLongClickListener(v -> {
            wibruj(100);
            kopiujDoSchowka(opisParametrow.getText().toString());
            return true;
        });
    }

    /**
     * @brief Konfiguruje pojedynczy wykres liniowy (styl, osie, dane).
     * @param chart referencja wykresu.
     * @param color kolor serii i etykiet.
     * @param min minimalna wartość osi Y (0, gdy auto-scale).
     * @param max maksymalna wartość osi Y (0, gdy auto-scale).
     */
    private void setupSingleChart(LineChart chart, int color, float min, float max) {
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setBackgroundColor(Color.parseColor("#222222"));

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        chart.setData(data);

        XAxis x = chart.getXAxis();
        x.setEnabled(false);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(color);
        if (max > 0) {
            left.setAxisMinimum(min);
            left.setAxisMaximum(max);
        }
        left.setDrawGridLines(true);
        left.setGridColor(Color.DKGRAY);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
    }

    /**
     * @brief Konfiguruje przyciski nawigacyjne i ich zachowania.
     * @details Przełącza ekrany, start/stop odświeżania i GPS, aktualizuje UI.
     */
    private void setupButtons() {
        View.OnClickListener listener = v -> {
            wibruj(30);
            stopRamRefresher();
            zatrzymajNasluchiwanieGPS();
            stopFirebaseRamListener();

            layoutWykresy.setVisibility(View.GONE);

            int id = v.getId();
            if (id == R.id.btn_ogolne) {
                aktualnieWybranyEkran = EKRAN_OGOLNE;
                wyswietlInformacjeOgolne();
                startRamRefresher();
            } else if (id == R.id.btn_gps) {
                aktualnieWybranyEkran = EKRAN_GPS;
                wyswietlInformacjeGPS();
                sprawdzIpoprosOPermISjeGPS();
            } else if (id == R.id.btn_zyroskop) {
                aktualnieWybranyEkran = EKRAN_ZYROSKOP;
                wyswietlZyroskop();
            } else if (id == R.id.btn_system) {
                aktualnieWybranyEkran = EKRAN_SYSTEM;
                layoutWykresy.setVisibility(View.VISIBLE);
                wyswietlInformacjeSystemowe();
                startRamRefresher();
            } else if (id == R.id.btn_aplikacja) {
                aktualnieWybranyEkran = EKRAN_APLIKACJA;
                opisParametrow.setText(daneAplikacjiTekst());
            }
        };

        int[] buttons = {R.id.btn_ogolne, R.id.btn_gps, R.id.btn_zyroskop, R.id.btn_system, R.id.btn_aplikacja};
        for (int id : buttons) {
            View btn = findViewById(id);
            if (btn != null) btn.setOnClickListener(listener);
        }
    }

    /* ==== LOGIKA PĘTLI POMIAROWEJ (MONITORING) ==== */

    /**
     * @brief Przygotowuje Runnable odświeżający dane systemowe i wykresy co 1s.
     * @details
     * - Zbiera RAM (MB), baterię (%), temperaturę (°C), częstotliwość CPU (GHz).
     * - Aktualizuje wykresy na ekranie systemowym.
     * - Uśrednia wartości i publikuje do Firebase co RAM_PUBLISH_INTERVAL_MS.
     * - Aktualizuje tekstowe ekrany ogólne/systemowe.
     */
    private void setupRamRefresher() {
        ramRunnable = new Runnable() {
            @Override public void run() {
                // 1. DANE RAM (MB)
                ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
                ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memInfo);
                double freeRamMB = memInfo.availMem / (1024.0 * 1024.0);
                double totalRamMB = memInfo.totalMem / (1024.0 * 1024.0);
                double usedRamMB = totalRamMB - freeRamMB;

                // 2. DANE BATERII I TEMP
                float batteryPct = 0;
                float batteryTemp = 0;
                Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (bat != null) {
                    int level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int tempInt = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    if (level != -1 && scale != -1) {
                        batteryPct = (level / (float)scale) * 100;
                    }
                    batteryTemp = tempInt / 10.0f;
                }

                // 3. DANE CPU (GHz)
                float cpuFreq = getCpuFreqFloat();

                // 4. AKTUALIZACJA WYKRESÓW
                if (aktualnieWybranyEkran == EKRAN_SYSTEM && layoutWykresy.getVisibility() == View.VISIBLE) {
                    updateSingleChart(chartRam, (float)usedRamMB, Color.GREEN);
                    updateSingleChart(chartBattery, batteryPct, Color.YELLOW);
                    updateSingleChart(chartTemp, batteryTemp, Color.RED);
                    updateSingleChart(chartLight, aktualneSwiatloLx, Color.CYAN);
                    updateSingleChart(chartCpu, cpuFreq, Color.MAGENTA);
                }

                sumRam += freeRamMB;
                sumTemp += batteryTemp;
                sumCpu += cpuFreq;
                countSamples++;

                // 5. WYSYŁKA DO FIREBASE
                long now = System.currentTimeMillis();
                if (firebasePolaczony && firebaseDeviceRef!=null && (now - lastPublishTs) >= RAM_PUBLISH_INTERVAL_MS) {
                    lastPublishTs = now;

                    double avgFreeRam = countSamples > 0 ? sumRam / countSamples : freeRamMB;
                    double avgTemp = countSamples > 0 ? sumTemp / countSamples : batteryTemp;
                    double avgCpu = countSamples > 0 ? sumCpu / countSamples : cpuFreq;

                    sumRam = 0;
                    sumTemp = 0;
                    sumCpu = 0;
                    countSamples = 0;

                    RamSample sample = new RamSample(
                            now,
                            zaokraglij(avgFreeRam, 0),
                            (int)batteryPct,
                            zaokraglij(avgTemp, 1),
                            zaokraglij(avgCpu, 2)
                    );

                    firebaseDeviceRef.push().setValue(sample)
                            .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Błąd zapisu: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                if (aktualnieWybranyEkran==EKRAN_OGOLNE) wyswietlInformacjeOgolne();
                else if (aktualnieWybranyEkran==EKRAN_SYSTEM) wyswietlInformacjeSystemowe();

                ramHandler.postDelayed(this,1000);
            }
        };
    }

    /**
     * @brief Odczytuje bieżącą częstotliwość CPU (cpu0) w GHz.
     * @return Częstotliwość CPU w GHz lub 0, gdy odczyt się nie powiódł.
     */
    private float getCpuFreqFloat() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "r");
            String line = reader.readLine();
            reader.close();
            return Float.parseFloat(line) / 1000000.0f;
        } catch (Exception e) {
            return 0;
        }
    }

    /* ==== METODY POMOCNICZE ==== */

    /**
     * @brief Zaokrągla wartość do podanej liczby miejsc po przecinku.
     * @param value wartość wejściowa.
     * @param places liczba miejsc po przecinku (>=0).
     * @return Wartość po zaokrągleniu.
     * @throws IllegalArgumentException gdy places < 0.
     */
    private double zaokraglij(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    /**
     * @brief Dodaje nowy punkt do wykresu i utrzymuje maks. liczbę punktów (rolling).
     * @param chart wykres, który ma zostać zaktualizowany.
     * @param val wartość Y.
     * @param color kolor serii (używany przy inicjalizacji).
     */
    private void updateSingleChart(LineChart chart, float val, int color) {
        if (chart == null) return;
        if (chart.getData() == null) chart.setData(new LineData());
        LineData data = chart.getData();
        LineDataSet set = (LineDataSet) data.getDataSetByIndex(0);

        if (set == null) {
            set = new LineDataSet(null, "Data");
            set.setColor(color);
            set.setLineWidth(2f);
            set.setDrawCircles(false);
            set.setDrawValues(false);
            set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            set.setDrawFilled(true);
            set.setFillColor(color);
            set.setFillAlpha(50);
            data.addDataSet(set);
        }

        data.addEntry(new Entry(set.getEntryCount(), val), 0);
        data.notifyDataChanged();

        if (set.getEntryCount() > MAX_LOCAL_POINTS) {
            set.removeFirst();
            for (int i=0; i<set.getEntryCount(); i++) {
                set.getEntryForIndex(i).setX(i);
            }
        }
        chart.notifyDataSetChanged();
        chart.moveViewToX(data.getEntryCount());
    }

    /* ==== MODEL DANYCH FIREBASE ==== */

    /**
     * @class RamSample
     * @brief Model próbki danych zapisywanych do Firebase.
     *
     * @details
     * Pola są publiczne, aby Firebase mógł je serializować/deserializować.
     */
    public static class RamSample {
        /** Znacznik czasu (ms since epoch). */
        public long czas;
        /** Wolna pamięć RAM [MB] (uśredniona). */
        public double ram_wolne;
        /** Poziom baterii [%]. */
        public int bateria_poziom;
        /** Temperatura baterii [°C] (uśredniona). */
        public double bateria_temp;
        /** Częstotliwość CPU [GHz] (uśredniona). */
        public double cpu_freq;

        /** Konstruktor bezargumentowy wymagany przez Firebase. */
        public RamSample() {}

        /**
         * @brief Tworzy próbkę RAM/CPU/Battery.
         * @param czas znacznik czasu (ms).
         * @param ram_wolne wolna RAM [MB].
         * @param bateria_poziom poziom baterii [%].
         * @param bateria_temp temperatura baterii [°C].
         * @param cpu_freq częstotliwość CPU [GHz].
         */
        public RamSample(long czas, double ram_wolne, int bateria_poziom, double bateria_temp, double cpu_freq){
            this.czas = czas;
            this.ram_wolne = ram_wolne;
            this.bateria_poziom = bateria_poziom;
            this.bateria_temp = bateria_temp;
            this.cpu_freq = cpu_freq;
        }
    }

    /* ==== OBSŁUGA SENSORÓW I SPRZĘTU ==== */

    /**
     * @brief Inicjalizuje dostępne sensory: światło, żyroskop, akcelerometr.
     */
    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    /**
     * @brief Wyszukuje kamerę z lampą błyskową i zapisuje jej ID do użycia jako latarka.
     */
    private void setupFlashlight() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null) {
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics ch = cameraManager.getCameraCharacteristics(id);
                    Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) { cameraId = id; break; }
                }
            }
        } catch (CameraAccessException ignored) {}
    }

    /**
     * @brief Konfiguruje manager lokalizacji, geocoder i listener lokalizacji.
     * @details Listener aktualizuje współrzędne, dokładność, UI oraz pobiera pogodę.
     */
    private void setupLocation() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        geocoder = new Geocoder(this, Locale.getDefault());
        locationListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                aktualnaSzerokosc = location.getLatitude();
                aktualnaDlugosc = location.getLongitude();
                aktualnaDokladnosc = location.getAccuracy();
                if (aktualnieWybranyEkran == EKRAN_GPS) {
                    wyswietlInformacjeGPS();
                    pobierzDanePogodowe(aktualnaSzerokosc, aktualnaDlugosc);
                }
            }
            @Override public void onStatusChanged(String provider,int status,Bundle extras){}
            @Override public void onProviderEnabled(@NonNull String provider){}
            @Override public void onProviderDisabled(@NonNull String provider){
                aktualnaSzerokosc=0.0; aktualnaDlugosc=0.0;
                if (aktualnieWybranyEkran==EKRAN_GPS) wyswietlInformacjeGPS();
            }
        };
    }

    /* ==== WYŚWIETLANIE EKRANÓW ==== */

    /**
     * @brief Zwraca opis danych aplikacji (wersja, status, uprawnienia, sensory).
     * @return Tekst do wyświetlenia.
     */
    private String daneAplikacjiTekst() {
        return "DANE APLIKACJI\n-----------------------------------\n\n" +
                "• Wersja: 2.0 (Kompletna)\n" +
                "• Status: Aktywna\n" +
                "• Ostatnia aktualizacja: Teraz\n\n" +
                "UPRAWNIENIA\n-----------------------------------\n" +
                " Lokalizacja (GPS/Sieć)\n" +
                " Internet (Pogoda/Mapy)\n" +
                " Stan telefonu (Bateria)\n" +
                " Aparat (Latarka)\n\n" +
                "WYKORZYSTYWANE SENSORY\n-----------------------------------\n" +
                " Żyroskop\n" +
                " Akcelerometr\n" +
                " Czujnik światła";
    }

    /**
     * @brief Buduje i wyświetla informacje ogólne o urządzeniu (RAM, dysk, bateria).
     */
    private void wyswietlInformacjeOgolne() {
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
        double total=mi.totalMem/(1024.0*1024.0*1024.0);
        double free=mi.availMem/(1024.0*1024.0*1024.0);

        Intent bs=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        String pct="---", temp="---", volt="---", tech="---", stat="---";
        if(bs!=null) {
            int level=bs.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            int scale=bs.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            if(level!=-1 && scale!=-1) pct=(int)((level/(float)scale)*100)+"%";
            temp=String.format(Locale.US,"%.1f°C", bs.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10.0f);
            volt=bs.getIntExtra(BatteryManager.EXTRA_VOLTAGE,0)+" mV";
            tech=bs.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
            if(tech==null) tech="Li-ion";
            int st=bs.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            if(st==BatteryManager.BATTERY_STATUS_CHARGING) stat="Ładuje się ⚡";
            else if(st==BatteryManager.BATTERY_STATUS_DISCHARGING) stat="Rozładowywanie";
            else if(st==BatteryManager.BATTERY_STATUS_FULL) stat="Naładowana 🔋";
            else stat="Nieznany";
        }

        double capacity = getBatteryCapacity(this);
        String capStr = (capacity > 0) ? String.format(Locale.US, "%.0f mAh", capacity) : "Nieznana";
        String dysk=getPojemnoscDysku();

        String txt=String.format(Locale.getDefault(),
                "INFORMACJE O URZĄDZENIU\n-----------------------------------\n• Model: %s\n• Producent: %s\n\nPAMIĘĆ I DYSK\n-----------------------------------\n• RAM Wolne: %.2f GB\n• RAM Całkowite: %.2f GB\n• DYSK %s\n\nBATERIA\n-----------------------------------\n• Poziom: %s\n• Pojemność: %s\n• Status: %s\n• Technologia: %s\n• Temperatura: %s\n• Napięcie: %s\n\n(Przytrzymaj tekst, aby skopiować)",
                Build.MODEL, Build.MANUFACTURER, free, total, dysk, pct, capStr, stat, tech, temp, volt);
        opisParametrow.setText(txt);
    }

    /**
     * @brief Buduje i wyświetla informacje GPS: adres, współrzędne, dokładność.
     * @note Oznaczona @SuppressLint dla lintera — rzeczywiste sprawdzenie uprawnień jest wykonywane.
     */
    @SuppressLint("MissingPermission")
    private void wyswietlInformacjeGPS() {
        StringBuilder sb=new StringBuilder("PARAMETRY GPS\n-----------------------------------\n\n");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            sb.append("🔴 Brak uprawnień GPS.");
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            sb.append("🔴 GPS wyłączony.");
        } else if (aktualnaSzerokosc==0.0) {
            sb.append("🟡 Szukanie satelitów...");
        } else {
            sb.append("• Adres: ").append(pobierzAdres(aktualnaSzerokosc,aktualnaDlugosc)).append("\n");
            sb.append(String.format(Locale.US,"• Szerokość: %.6f\n",aktualnaSzerokosc));
            sb.append(String.format(Locale.US,"• Długość: %.6f\n",aktualnaDlugosc));
            sb.append(String.format(Locale.US,"• Dokładność: %.1f m\n",aktualnaDokladnosc));
            sb.append("\n🗺️ Kliknij by otworzyć mapę");
        }
        opisParametrow.setText(sb.toString());
    }

    /**
     * @brief Buduje i wyświetla informacje systemowe: CPU, światło, Android, latarka, ekran.
     */
    private void wyswietlInformacjeSystemowe() {
        DisplayMetrics m=new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(m);
        int cores=Runtime.getRuntime().availableProcessors();
        String abi=(Build.SUPPORTED_ABIS!=null && Build.SUPPORTED_ABIS.length>0)? Build.SUPPORTED_ABIS[0]:"N/D";
        String cpu=getCpuFreq();
        String torch=isFlashlightOn? "Włączona 💡":"Wyłączona";

        float cpuVal = getCpuFreqFloat();

        String s=String.format(Locale.US,
                "PROCESOR\n-----------------------------------\n• Rdzenie: %d\n• Taktowanie: %.2f GHz\n• Architektura: %s\n\nINFORMACJE SYSTEMOWE\n-----------------------------------\n• Światło: %.1f lx\n• Android: %s (API %d)\n• Latarka: %s (Kliknij)\n\nEKRAN\n-----------------------------------\n• Rozdzielczość: %dx%d px\n• Gęstość: %d dpi\n\n(Poniżej wykres użycia pamięci RAM na żywo)",
                cores,cpuVal,abi,aktualneSwiatloLx,Build.VERSION.RELEASE,Build.VERSION.SDK_INT,torch,m.widthPixels,m.heightPixels,m.densityDpi);
        opisParametrow.setText(s);
    }

    /**
     * @brief Wyświetla bieżące odczyty żyroskopu (rad/s) i akcelerometru (m/s²).
     */
    private void wyswietlZyroskop() {
        String s=String.format(Locale.US,
                "ŻYROSKOP (rad/s)\n-----------------------------------\nX: %.2f\nY: %.2f\nZ: %.2f\n\nAKCELEROMETR (m/s²)\n-----------------------------------\nX: %.2f\nY: %.2f\nZ: %.2f\n\n(Przytrzymaj tekst, aby skopiować)",
                aktualnyZyroskop[0],aktualnyZyroskop[1],aktualnyZyroskop[2],
                aktualnyAkcelerometr[0],aktualnyAkcelerometr[1],aktualnyAkcelerometr[2]);
        opisParametrow.setText(s);
    }

    /* ==== POZOSTAŁE METODY POMOCNICZE (SYSTEMOWE) ==== */

    /**
     * @brief Odczytuje uśrednioną pojemność baterii z PowerProfile (mAh).
     * @param context kontekst aplikacji.
     * @return Pojemność baterii w mAh lub 0 przy błędzie.
     */
    private double getBatteryCapacity(Context context) {
        Object mPowerProfile;
        double batteryCapacity = 0;
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
        try {
            mPowerProfile = Class.forName(POWER_PROFILE_CLASS)
                    .getConstructor(Context.class)
                    .newInstance(context);
            batteryCapacity = (Double) Class.forName(POWER_PROFILE_CLASS)
                    .getMethod("getAveragePower", String.class)
                    .invoke(mPowerProfile, "battery.capacity");
        } catch (Exception e) { e.printStackTrace(); }
        return batteryCapacity;
    }

    /**
     * @brief Zwraca opis pojemności i wolnego miejsca na dysku danych aplikacji.
     * @return Tekst z wolnym i całkowitym miejscem [GB].
     */
    private String getPojemnoscDysku() {
        try {
            File path= Environment.getDataDirectory();
            StatFs stat=new StatFs(path.getPath());
            long blk=stat.getBlockSizeLong();
            long t=stat.getBlockCountLong();
            long a=stat.getAvailableBlocksLong();
            double total=(t*blk)/(1024.0*1024.0*1024.0);
            double free=(a*blk)/(1024.0*1024.0*1024.0);
            return String.format(Locale.US,"Wolne: %.2f GB\n• Całkowite: %.2f GB",free,total);
        } catch (Exception e){ return "Błąd odczytu"; }
    }

    /**
     * @brief Próbuje zgeokodować współrzędne na zwięzły adres (miejscowość + ulica).
     * @param lat szerokość geograficzna.
     * @param lon długość geograficzna.
     * @return Sformatowany adres lub komunikat o niedostępności.
     */
    private String pobierzAdres(double lat,double lon) {
        if(!Geocoder.isPresent()) return "Geokodowanie niedostępne";
        try {
            List<Address> list=geocoder.getFromLocation(lat,lon,1);
            if(list!=null && !list.isEmpty()) {
                Address a=list.get(0);
                String loc=a.getLocality()!=null? a.getLocality():"";
                String th=a.getThoroughfare()!=null? a.getThoroughfare():"";
                return (loc+" "+th).trim();
            }
        } catch (Exception ignored){}
        return "Adres nieznany";
    }

    /**
     * @brief Odczytuje częstotliwość CPU (cpu0) i zwraca jako tekst "x.xx GHz".
     * @return Tekstowa reprezentacja częstotliwości CPU lub "Nieznane".
     */
    private String getCpuFreq() {
        try (RandomAccessFile r=new RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq","r")) {
            String line=r.readLine();
            if(line!=null) {
                long f=Long.parseLong(line);
                return String.format(Locale.US,"%.2f GHz", f/1_000_000.0);
            }
        } catch (Exception ignored){}
        return "Nieznane";
    }

    /**
     * @brief Sprawdza i (jeśli potrzeba) prosi o uprawnienia GPS, a następnie uruchamia nasłuchiwanie.
     */
    private void sprawdzIpoprosOPermISjeGPS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            uruchomNasluchiwanieGPS();
        } else {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_CODE_GPS_PERMISSION);
        }
    }

    /**
     * @brief Rejestruje nasłuchiwanie lokalizacji z providerów GPS/NETWORK (jeśli dostępni).
     * @note Oznaczone @SuppressLint — rzeczywiste sprawdzenie uprawnień wykonywane przed wywołaniem.
     */
    @SuppressLint("MissingPermission")
    private void uruchomNasluchiwanieGPS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,5,locationListener);
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,2000,5,locationListener);
        } catch (Exception ignored){}
    }

    /**
     * @brief Zatrzymuje nasłuchiwanie lokalizacji.
     */
    private void zatrzymajNasluchiwanieGPS() {
        if(locationManager!=null && locationListener!=null) {
            try { locationManager.removeUpdates(locationListener); } catch (SecurityException ignored){}
        }
    }

    /**
     * @brief Pobiera dane pogodowe z OpenWeather dla podanych współrzędnych.
     * @param lat szerokość geograficzna.
     * @param lon długość geograficzna.
     * @details Wykonuje zapytanie w ExecutorService i aktualizuje UI na głównym wątku.
     */
    private void pobierzDanePogodowe(double lat,double lon) {
        executorService.execute(() -> {
            String resp="";
            try {
                String urlString=String.format(Locale.US,WEATHER_URL,lat,lon,API_KEY);
                HttpURLConnection c=(HttpURLConnection)new URL(urlString).openConnection();
                c.setRequestMethod("GET"); c.setConnectTimeout(5000);
                if (c.getResponseCode()==HttpURLConnection.HTTP_OK) {
                    BufferedReader in=new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb=new StringBuilder(); String line;
                    while((line=in.readLine())!=null) sb.append(line);
                    in.close(); resp=sb.toString();
                }
            } catch (Exception ignored){}
            String finalResp=resp;
            mainHandler.post(() -> { if(aktualnieWybranyEkran==EKRAN_GPS && !finalResp.isEmpty()) sformatujIWyswietlPogode(finalResp); });
        });
    }

    /**
     * @brief Formatuje JSON odpowiedzi pogodowej i dokleja blok do aktualnego opisu.
     * @param json surowy JSON z OpenWeather.
     */
    private void sformatujIWyswietlPogode(String json) {
        try {
            JSONObject j=new JSONObject(json);
            String miasto=j.optString("name","Nieznane");
            JSONObject main=j.getJSONObject("main");
            JSONObject w=j.getJSONArray("weather").getJSONObject(0);
            JSONObject wind=j.getJSONObject("wind");
            String opis=w.optString("description","");
            if(!opis.isEmpty()) opis=opis.substring(0,1).toUpperCase()+opis.substring(1);
            String blok=String.format(Locale.getDefault(),
                    "\n\n───── POGODA: %s ────\n🌡️ Temp: %.1f°C (Odczuwalna: %.1f°C)\n☁️ Niebo: %s\n------------------------------------\n💧 Wilgotność: %d%%\n⏱️ Ciśnienie: %d hPa\n💨 Wiatr: %.1f m/s",
                    miasto.toUpperCase(), main.getDouble("temp"), main.getDouble("feels_like"),
                    opis, main.getInt("humidity"), main.getInt("pressure"), wind.getDouble("speed"));
            String base=opisParametrow.getText().toString();
            String sep="───── POGODA:";
            if(base.contains(sep)) {
                int idx=base.indexOf("\n\n"+sep);
                if(idx>=0) base=base.substring(0,idx);
            }
            opisParametrow.setText(base+blok);
        } catch (Exception ignored){}
    }

    /**
     * @brief Uruchamia pętlę odświeżania danych systemowych (co 1s).
     */
    private void startRamRefresher() {
        ramHandler.removeCallbacks(ramRunnable);
        ramHandler.post(ramRunnable);
    }

    /**
     * @brief Zatrzymuje pętlę odświeżania danych systemowych.
     */
    private void stopRamRefresher() {
        ramHandler.removeCallbacks(ramRunnable);
    }

    /**
     * @brief Przykładowe przygotowanie listenera Firebase (nieużywany).
     */
    private void startFirebaseRamListener() {
        if (!firebasePolaczony || firebaseDeviceRef==null || ramFirebaseListener!=null) return;
        ramFirebaseListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
    }

    /**
     * @brief Usuwa listener RAM z Firebase, jeśli był dodany.
     */
    private void stopFirebaseRamListener() {
        if (firebaseDeviceRef!=null && ramFirebaseListener!=null) {
            firebaseDeviceRef.removeEventListener(ramFirebaseListener);
            ramFirebaseListener=null;
        }
    }

    /**
     * @brief Otwiera aplikację map z pinezką w bieżącej lokalizacji.
     */
    private void otworzMapyGoogle() {
        try {
            String uri = String.format(Locale.US,"geo:%f,%f?q=%f,%f(Tu jesteś)",aktualnaSzerokosc,aktualnaDlugosc,aktualnaSzerokosc,aktualnaDlugosc);
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
        } catch (Exception e) {
            Toast.makeText(this,"Nie znaleziono aplikacji map.",Toast.LENGTH_LONG).show();
        }
    }

    /**
     * @brief Przełącza latarkę (torch) jeśli dostępna i posiada uprawnienia.
     * @details Aktualizuje opis ekranu systemowego po przełączeniu.
     */
    private void przelaczLatarke() {
        if (cameraManager==null || cameraId==null) {
            Toast.makeText(this,"Brak lampy błyskowej",Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},REQUEST_CODE_CAMERA_PERMISSION);
            return;
        }
        try {
            cameraManager.setTorchMode(cameraId, !isFlashlightOn);
            isFlashlightOn = !isFlashlightOn;
            if (aktualnieWybranyEkran==EKRAN_SYSTEM) wyswietlInformacjeSystemowe();
            Toast.makeText(this,isFlashlightOn? "Latarka włączona":"Latarka wyłączona",Toast.LENGTH_SHORT).show();
        } catch (CameraAccessException | IllegalArgumentException e) {
            Toast.makeText(this,"Błąd latarki",Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @brief Wibruje urządzeniem przez podany czas.
     * @param ms czas wibracji w milisekundach.
     */
    @SuppressLint("MissingPermission")
    private void wibruj(int ms) {
        Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        if (v!=null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        }
    }

    /**
     * @brief Kopiuje tekst do schowka i pokazuje komunikat.
     * @param txt tekst do skopiowania.
     */
    private void kopiujDoSchowka(String txt) {
        ClipboardManager cb=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        cb.setPrimaryClip(ClipData.newPlainText("Dane",txt));
        Toast.makeText(this,"Skopiowano",Toast.LENGTH_SHORT).show();
    }

    /**
     * @brief Callback dla wyników żądań uprawnień (GPS/Kamera).
     * @param requestCode kod żądania.
     * @param permissions tablica żądanych uprawnień.
     * @param grantResults wyniki przyznania.
     */
    @Override public void onRequestPermissionsResult(int requestCode,@NonNull String[] permissions,@NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==REQUEST_CODE_GPS_PERMISSION) {
            if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
                uruchomNasluchiwanieGPS();
                if(aktualnieWybranyEkran==EKRAN_GPS) wyswietlInformacjeGPS();
            } else Toast.makeText(this,"Brak zgody na GPS",Toast.LENGTH_LONG).show();
        } else if (requestCode==REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
                przelaczLatarke();
            } else Toast.makeText(this,"Brak zgody na kamerę (latarka)",Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * @brief Obsługa zdarzeń sensorów: światło, żyroskop, akcelerometr.
     * @param e zdarzenie sensora.
     */
    @Override public void onSensorChanged(SensorEvent e) {
        int t=e.sensor.getType();
        if (t==Sensor.TYPE_LIGHT) {
            aktualneSwiatloLx=e.values[0];
            if(aktualnieWybranyEkran==EKRAN_SYSTEM) wyswietlInformacjeSystemowe();
        } else if (t==Sensor.TYPE_GYROSCOPE) {
            aktualnyZyroskop[0]=e.values[0]; aktualnyZyroskop[1]=e.values[1]; aktualnyZyroskop[2]=e.values[2];
            if(aktualnieWybranyEkran==EKRAN_ZYROSKOP) wyswietlZyroskop();
        } else if (t==Sensor.TYPE_ACCELEROMETER) {
            aktualnyAkcelerometr[0]=e.values[0]; aktualnyAkcelerometr[1]=e.values[1]; aktualnyAkcelerometr[2]=e.values[2];
            if(aktualnieWybranyEkran==EKRAN_ZYROSKOP) wyswietlZyroskop();
        }
    }

    /**
     * @brief Nieużywane: zmiana dokładności sensora.
     * @param sensor sensor.
     * @param acc nowa dokładność.
     */
    @Override public void onAccuracyChanged(Sensor sensor,int acc){}
}