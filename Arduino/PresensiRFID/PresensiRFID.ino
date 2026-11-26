#include <SPI.h>
#include <MFRC522.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <NetworkClient.h>
#include <StreamString.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include "ReaderConfig.h"

// --- PENGATURAN LCD I2C ---
LiquidCrystal_I2C lcd(0x27, 16, 2);

const uint16_t HTTP_TIMEOUT_MS = 5000;

// --- PENGATURAN PIN RFID ---
#define SS_1_PIN 5   // SDA Sensor 1 (Ruang 17)
#define RST_1_PIN 27 // RST Sensor 1
  
#define SS_2_PIN 4   // SDA Sensor 2 (Lab Komputer)
#define RST_2_PIN 26 // RST Sensor 2

MFRC522 sensor_ruang17(SS_1_PIN, RST_1_PIN);
MFRC522 sensor_lab(SS_2_PIN, RST_2_PIN);

// --- VARIABEL NON-BLOCKING LCD SCROLLING (1 KALI) ---
enum LcdState {
  LCD_STANDBY,
  LCD_STATIC_SHOW,
  LCD_SCROLL_START_PAUSE,
  LCD_SCROLLING,
  LCD_SCROLL_END_PAUSE
};

LcdState currentLcdState = LCD_STANDBY;
String scrollText = "";
int scrollIndex = 0;
unsigned long lcdTimer = 0;

// Pengaturan kecepatan & jeda tampilan LCD (dalam milidetik)
const unsigned long SCROLL_SPEED_MS = 250;     // Kecepatan geser per huruf
const unsigned long START_END_PAUSE_MS = 600;  // Jeda baca di awal & akhir kalimat
const unsigned long STATIC_SHOW_MS = 2000;     // Lama tampil jika teks <= 16 karakter

// --- VARIABEL ANTI-DOUBLE SCAN PER SENSOR ---
String lastUid1 = "";
unsigned long lastScanTime1 = 0;

String lastUid2 = "";
unsigned long lastScanTime2 = 0;

const unsigned long SAME_CARD_COOLDOWN_MS = 2500; // Jeda minimal untuk kartu yang SAMA

void setup() {
  Serial.begin(115200);
  
  // Inisialisasi I2C LCD pada pin 21 (SDA) dan 22 (SCL)
  Wire.begin(21, 22);
  lcd.init();                      
  lcd.backlight();
  
  lcd.setCursor(0, 0);
  lcd.print("Sistem Absensi");
  lcd.setCursor(0, 1);
  lcd.print("Menghubungkan...");

  SPI.begin();
  sensor_ruang17.PCD_Init();
  sensor_lab.PCD_Init();

  // Koneksi WiFi
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long mulaiWifi = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - mulaiWifi < 15000) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\nWiFi Terhubung!");
    mulaiTampilkanPesan("WiFi Terhubung!", "Siap Scan Kartu");
    delay(1500);
    tampilkanStatusStandby();
  } else {
    mulaiTampilkanPesan("Koneksi Gagal", "WiFi Terputus!");
  }
}

void loop() {
  updateLcdDisplay();
  periksaSensor(sensor_ruang17, "17", "Ruang 17", lastUid1, lastScanTime1);
  periksaSensor(sensor_lab, "LAB", "Lab Komputer", lastUid2, lastScanTime2);
}

void periksaSensor(MFRC522 &sensor, const char *idRuangan, const char *label,
                   String &uidTerakhir, unsigned long &waktuTerakhir) {
  if (!sensor.PICC_IsNewCardPresent() || !sensor.PICC_ReadCardSerial()) {
    return;
  }
  String uid = getUID(sensor);
  sensor.PICC_HaltA();
  sensor.PCD_StopCrypto1();
  if (uid != uidTerakhir || millis() - waktuTerakhir > SAME_CARD_COOLDOWN_MS) {
    uidTerakhir = uid;
    waktuTerakhir = millis();
    kirimDataKeServer(uid, idRuangan, label);
  }
}

String getUID(MFRC522 &mfrc522) {
  String uidStr = "";
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    uidStr += String(mfrc522.uid.uidByte[i] < 0x10 ? "0" : "");
    uidStr += String(mfrc522.uid.uidByte[i], HEX);
  }
  uidStr.toUpperCase();
  return uidStr;
}

void kirimDataKeServer(const String &uid, const char *idRuangan, const char *label) {
  if (WiFi.status() != WL_CONNECTED) {
    mulaiTampilkanPesan("Koneksi Gagal", "WiFi Terputus!");
    return;
  }
  String url = READER_BASE_URL;
  if (!url.startsWith("http://") || !READER_TOKEN[0]) {
    mulaiTampilkanPesan("Konfigurasi", "Periksa reader");
    return;
  }

  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Memproses...");
  lcd.setCursor(0, 1);
  lcd.print(label);

  if (url.endsWith("/")) {
    url.remove(url.length() - 1);
  }
  url += "/proses_rfid";

  NetworkClient lan;
  HTTPClient http;
  if (!http.begin(lan, url)) {
    mulaiTampilkanPesan("Konfigurasi", "URL reader salah");
    return;
  }
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  http.setReuse(false);
  http.setFollowRedirects(HTTPC_DISABLE_FOLLOW_REDIRECTS);
  http.addHeader("Content-Type", "application/x-www-form-urlencoded");
  http.addHeader("X-Reader-Token", READER_TOKEN);
  const char *header[] = {"X-Presensi-Hasil"};
  http.collectHeaders(header, 1);
  // Satu POST per tap. Jika koneksi putus, cek dashboard sebelum menempel lagi.
  int kode = http.POST("uid=" + uid + "&id_ruangan=" + idRuangan);
  String hasil = http.header("X-Presensi-Hasil");
  bool diterima = kode == 200 && (hasil == "masuk" || hasil == "keluar");
  bool ditolak = kode == 409 && (hasil == "tolak" || hasil == "kartu_baru");
  bool galatServer = kode == 500 && hasil == "error";

  if (kode <= 0) {
    mulaiTampilkanPesan("Koneksi Gagal", "Cek status di dashboard");
  } else if (diterima || ditolak || galatServer) {
    StreamString pesan;
    int dibaca = http.writeToStream(&pesan);
    if (dibaca <= 0 || (http.getSize() >= 0 && dibaca != http.getSize())) {
      mulaiTampilkanPesan("Koneksi Gagal", "Cek status di dashboard");
    } else {
      mulaiTampilkanPesan(galatServer ? "Error Server" : label, pesan);
    }
  } else {
    mulaiTampilkanPesan("Kesalahan HTTP", "Kode: " + String(kode));
  }
  Serial.printf("Respons tap %s: %d\n", idRuangan, kode);
  http.end();
}

// Memulai state tampilan pesan baru (otomatis memutus scroll sebelumnya jika ada scan baru)
void mulaiTampilkanPesan(String barisAtas, String barisBawah) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(barisAtas);

  scrollText = barisBawah;
  scrollIndex = 0;
  lcdTimer = millis();

  if (scrollText.length() <= 16) {
    lcd.setCursor(0, 1);
    lcd.print(scrollText);
    currentLcdState = LCD_STATIC_SHOW;
  } else {
    lcd.setCursor(0, 1);
    lcd.print(scrollText.substring(0, 16));
    currentLcdState = LCD_SCROLL_START_PAUSE;
  }
}

// State machine untuk mengatur pergeseran teks 1 kali tanpa menghentikan loop()
void updateLcdDisplay() {
  unsigned long now = millis();

  switch (currentLcdState) {
    case LCD_STANDBY:
      // Diam di layar standby
      break;

    case LCD_STATIC_SHOW:
      if (now - lcdTimer >= STATIC_SHOW_MS) {
        tampilkanStatusStandby();
      }
      break;

    case LCD_SCROLL_START_PAUSE:
      // Tahan awal kalimat sebentar agar mudah terbaca sebelum mulai bergeser
      if (now - lcdTimer >= START_END_PAUSE_MS) {
        lcdTimer = now;
        currentLcdState = LCD_SCROLLING;
      }
      break;

    case LCD_SCROLLING:
      if (now - lcdTimer >= SCROLL_SPEED_MS) {
        lcdTimer = now;
        scrollIndex++;
        
        if (scrollIndex <= (int)scrollText.length() - 16) {
          lcd.setCursor(0, 1);
          lcd.print(scrollText.substring(scrollIndex, scrollIndex + 16));
        } else {
          // Sudah mencapai ujung akhir kalimat (selesai 1 kali rolling)
          currentLcdState = LCD_SCROLL_END_PAUSE;
        }
      }
      break;

    case LCD_SCROLL_END_PAUSE:
      // Tahan akhir kalimat sebentar sebelum kembali ke layar standby
      if (now - lcdTimer >= START_END_PAUSE_MS) {
        tampilkanStatusStandby();
      }
      break;
  }
}

void tampilkanStatusStandby() {
  currentLcdState = LCD_STANDBY;
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("Silahkan Tempel");
  lcd.setCursor(0, 1);
  lcd.print("Kartu Anda...");
}
