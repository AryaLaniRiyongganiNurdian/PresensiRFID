#include <SPI.h>
#include <MFRC522.h>
#include <WiFi.h>
#include <HTTPClient.h>

const char* ssid     = "HGW-81449D";
const char* password = "inilani12321";
const String serverIP   = "192.168.1.2";  // IPv4 komputer (cek dengan ipconfig)
const int    serverPort = 8080;

#define SS_1_PIN  5    // Ruang 17
#define RST_1_PIN 22
#define SS_2_PIN  4    // Lab Informatika
#define RST_2_PIN 21

MFRC522 sensor_ruang17(SS_1_PIN, RST_1_PIN);
MFRC522 sensor_lab(SS_2_PIN, RST_2_PIN);

void setup() {
  Serial.begin(115200);
  SPI.begin();

  sensor_ruang17.PCD_Init();
  sensor_lab.PCD_Init();

  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  Serial.print("Menghubungkan ke WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Terhubung! IP ESP32: " + WiFi.localIP().toString());
}

void loop() {
  bacaSensor(sensor_ruang17, "17");
  bacaSensor(sensor_lab, "LAB");
}

void bacaSensor(MFRC522 &sensor, const String &idRuangan) {
  if (!sensor.PICC_IsNewCardPresent() || !sensor.PICC_ReadCardSerial()) {
    return;
  }
  String uid = getUID(sensor);
  Serial.println("Kartu terbaca: " + uid + " @ " + idRuangan);
  kirimDataKeServer(uid, idRuangan);
  sensor.PICC_HaltA();
  sensor.PCD_StopCrypto1();
  delay(2000);
}

String getUID(MFRC522 &mfrc522) {
  String uidStr = "";
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    if (mfrc522.uid.uidByte[i] < 0x10) uidStr += "0";
    uidStr += String(mfrc522.uid.uidByte[i], HEX);
  }
  uidStr.toUpperCase();
  return uidStr;
}

void kirimDataKeServer(const String &uid, const String &idRuangan) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("Koneksi WiFi terputus!");
    WiFi.reconnect();
    return;
  }

  HTTPClient http;
  String url = "http://" + serverIP + ":" + String(serverPort) +
               "/absensi/proses_rfid?uid=" + uid + "&id_ruangan=" + idRuangan;

  http.setTimeout(5000);
  http.begin(url);
  int code = http.GET();

  if (code > 0) {
    Serial.println(http.getString());
  } else {
    Serial.println("Error HTTP: " + http.errorToString(code));
  }
  http.end();
}