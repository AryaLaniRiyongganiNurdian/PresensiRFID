@echo off
set MYSQL="C:\Users\Lani\Downloads\Programs\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe"
set FOLDER=C:\Users\Lani\Downloads\Programs
set LOG=%FOLDER%\log_impor.txt

echo Mulai impor %date% %time% > "%LOG%"

echo Membuat tabel dari Query.sql >> "%LOG%"
%MYSQL% -u root < "%FOLDER%\Query.sql" >> "%LOG%" 2>&1

for %%F in (seed_dosen.sql seed_mahasiswa.sql seed_log_presensi_bagian1.sql seed_log_presensi_bagian2.sql) do (
  echo Mulai %%F >> "%LOG%"
  %MYSQL% -u root --init-command="SET foreign_key_checks=0, unique_checks=0" db_absensi < "%FOLDER%\%%F" >> "%LOG%" 2>&1
  echo Selesai %%F >> "%LOG%"
)

echo SEMUA SELESAI %date% %time% >> "%LOG%"