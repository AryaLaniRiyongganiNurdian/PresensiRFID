#include <SPI.h>
#include <MFRC522.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// --- PENGATURAN LCD I2C ---
LiquidCrystal_I2C lcd(0x27, 16, 2);

// --- PENGATURAN WIFI & SERVER ---
const char* ssid = "HGW-81449D";
const char* password = "inilani12321";
const String serverIP = "192.168.1.6"; // IP Komputer tempat Web Java berjalan (Tomcat port 8080)

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
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  
  Serial.println("\nWiFi Terhubung!");
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print("WiFi Terhubung!");
  lcd.setCursor(0, 1);
  lcd.print("Siap Scan Kartu");
  delay(1500);
  
  tampilkanStatusStandby();
}

void loop() {
  // 1. Jalankan pembaruan animasi LCD secara non-blocking
  updateLcdDisplay();

  // 2. Cek Sensor Ruang 17 (ID: 17) - Bisa langsung scan meski LCD sedang rolling
  if (sensor_ruang17.PICC_IsNewCardPresent() && sensor_ruang17.PICC_ReadCardSerial()) {
    String uid = getUID(sensor_ruang17);
    Serial.println("UID terbaca: " + uid);
    sensor_ruang17.PICC_HaltA();
    sensor_ruang17.PCD_StopCrypto1();

    // Cek apakah kartu berbeda ATAU kartu sama tapi sudah melewati cooldown
    if (uid != lastUid1 || (millis() - lastScanTime1 > SAME_CARD_COOLDOWN_MS)) {
      lastUid1 = uid;
      lastScanTime1 = millis();
      kirimDataKeServer(uid, "17", "Ruang 17");
    }
  }

  // 3. Cek Sensor Lab Komputer (ID: LAB) - Bisa langsung scan kapan saja
  if (sensor_lab.PICC_IsNewCardPresent() && sensor_lab.PICC_ReadCardSerial()) {
    String uid = getUID(sensor_lab);
    Serial.println("UID terbaca: " + uid);
    sensor_lab.PICC_HaltA();
    sensor_lab.PCD_StopCrypto1();

    if (uid != lastUid2 || (millis() - lastScanTime2 > SAME_CARD_COOLDOWN_MS)) {
      lastUid2 = uid;
      lastScanTime2 = millis();
      kirimDataKeServer(uid, "LAB", "Lab Komputer");
    }
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

void kirimDataKeServer(String uid, String id_ruangan, String namaRuangLabel) {
  if (WiFi.status() == WL_CONNECTED) {
    lcd.clear();
    lcd.setCursor(0, 0);
    lcd.print("Memproses...");
    lcd.setCursor(0, 1);
    lcd.print(namaRuangLabel);

    HTTPClient http;
    String url = "http://" + serverIP + ":8080/PresensiRFID/proses_rfid?uid=" + uid + "&id_ruangan=" + id_ruangan;
    
    http.begin(url);
    int httpResponseCode = http.GET();
    
    if (httpResponseCode > 0) {
      String responDB = http.getString();
      responDB.trim(); // Menghapus spasi/newline tersembunyi dari server
      Serial.println(responDB);
      
      mulaiTampilkanPesan(namaRuangLabel, responDB);
    } else {
      Serial.println("Error HTTP: " + String(httpResponseCode));
      mulaiTampilkanPesan("Error Server", "Kode: " + String(httpResponseCode));
    }
    http.end();
  } else {
    Serial.println("Koneksi WiFi Terputus!");
    mulaiTampilkanPesan("Koneksi Gagal", "WiFi Terputus!");
  }
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
