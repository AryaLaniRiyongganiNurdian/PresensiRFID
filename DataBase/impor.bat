@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "SKEMA=%~dp0Query.sql"
if not defined KITAHADIR_MYSQL_EXE set "KITAHADIR_MYSQL_EXE=mysql.exe"
call :validasi_port
if errorlevel 1 exit /b 1
if not defined KITAHADIR_SEED_DIR set "KITAHADIR_SEED_DIR=%~dp0"
set "SEED=seed_dosen.sql seed_mahasiswa.sql seed_log_presensi_bagian1.sql seed_log_presensi_bagian2.sql"
set "LOG=%KITAHADIR_SEED_DIR%\log_impor.txt"
set "MYSQL_LOGIN="
set "MYSQL_HOST="
set "MYSQL_USER="
set "TARGET_HOST=host dari login path"
if defined KITAHADIR_MYSQL_LOGIN_PATH (
  set MYSQL_LOGIN=--login-path="%KITAHADIR_MYSQL_LOGIN_PATH%"
) else (
  if not defined KITAHADIR_MYSQL_HOST set "KITAHADIR_MYSQL_HOST=127.0.0.1"
  if not defined KITAHADIR_MYSQL_USER set "KITAHADIR_MYSQL_USER=root"
)
if defined KITAHADIR_MYSQL_HOST (
  set MYSQL_HOST=--host="%KITAHADIR_MYSQL_HOST%"
  set "TARGET_HOST=%KITAHADIR_MYSQL_HOST%"
)
if defined KITAHADIR_MYSQL_USER set MYSQL_USER=--user="%KITAHADIR_MYSQL_USER%"

if not exist "%SKEMA%" (
  >&2 echo Skema tidak ditemukan: "%SKEMA%"
  exit /b 1
)
for %%F in (%SEED%) do (
  if not exist "%KITAHADIR_SEED_DIR%\%%F" (
    >&2 echo Berkas seed tidak ditemukan: "%KITAHADIR_SEED_DIR%\%%F"
    exit /b 1
  )
)

> "%LOG%" echo Mulai impor %date% %time%
if errorlevel 1 (
  >&2 echo Tidak dapat menulis log: "%LOG%"
  exit /b 1
)
echo Target uji: "%TARGET_HOST%" port %KITAHADIR_MYSQL_PORT%, skema db_absensi.
>> "%LOG%" echo Target uji: "%TARGET_HOST%" port %KITAHADIR_MYSQL_PORT%, skema db_absensi.
call :mysql --version >> "%LOG%" 2>&1
if errorlevel 1 (
  >&2 echo MySQL CLI tidak dapat dijalankan. Periksa KITAHADIR_MYSQL_EXE dan "%LOG%".
  exit /b 1
)
call :mysql --batch --skip-column-names --execute="SELECT CONCAT('Instance MySQL: ', @@hostname, ':', @@port)" >> "%LOG%" 2>&1
if errorlevel 1 (
  >&2 echo Gagal menyambung ke target uji. Periksa pengaturan MySQL dan "%LOG%".
  exit /b 1
)
findstr /B /C:"Instance MySQL: " "%LOG%"
if errorlevel 1 (
  >&2 echo Identitas instance MySQL tidak ditemukan. Periksa "%LOG%".
  exit /b 1
)
call :konfirmasi
if errorlevel 1 (
  >> "%LOG%" echo Impor dibatalkan sebelum Query.sql.
  >&2 echo Impor dibatalkan sebelum Query.sql.
  exit /b 1
)

>> "%LOG%" echo Pengguna mengonfirmasi reset db_absensi pada instance di atas.
>> "%LOG%" echo Mulai Query.sql
call :mysql < "%SKEMA%" >> "%LOG%" 2>&1
if errorlevel 1 (
  >&2 echo Gagal menjalankan Query.sql. Periksa "%LOG%".
  exit /b 1
)
for %%F in (%SEED%) do (
  >> "%LOG%" echo Mulai %%F
  call :mysql --database=db_absensi < "%KITAHADIR_SEED_DIR%\%%F" >> "%LOG%" 2>&1
  if errorlevel 1 (
    >&2 echo Gagal mengimpor %%F. Periksa "%LOG%".
    exit /b 1
  )
)

>> "%LOG%" echo Selesai %date% %time%
echo Impor selesai. Log: "%LOG%"
exit /b 0

:mysql
"%KITAHADIR_MYSQL_EXE%" --no-defaults %MYSQL_LOGIN% --protocol=TCP %MYSQL_HOST% --port="%KITAHADIR_MYSQL_PORT%" %MYSQL_USER% --default-character-set=utf8mb4 %*
exit /b %errorlevel%

:konfirmasi
setlocal EnableDelayedExpansion
set "jawaban="
echo Query.sql akan menghapus tabel db_absensi pada instance di atas.
set /p "jawaban=Ketik YA untuk melanjutkan: "
if /I "!jawaban!"=="YA" (
  endlocal
  exit /b 0
)
endlocal
exit /b 1

:validasi_port
setlocal EnableDelayedExpansion
set "port=!KITAHADIR_MYSQL_PORT!"
if not defined port goto port_tidak_valid
set "sisa=#!port!"
for %%D in (0 1 2 3 4 5 6 7 8 9) do set "sisa=!sisa:%%D=!"
if not "!sisa!"=="#" goto port_tidak_valid
if "!port:~0,1!"=="0" goto port_tidak_valid
if not "!port:~5!"=="" goto port_tidak_valid
if !port! GTR 65535 goto port_tidak_valid
endlocal
exit /b 0

:port_tidak_valid
>&2 echo Tetapkan KITAHADIR_MYSQL_PORT ke port instance uji ^(1-65535, tanpa nol di depan^).
endlocal
exit /b 1
