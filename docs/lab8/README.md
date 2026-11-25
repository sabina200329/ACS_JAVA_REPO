# Lab 8 – Instakill pe Linux (WSL)

Backend-ul rulează în **Ubuntu (WSL)** cu **JDK 21** și **PostgreSQL**, iar frontend-ul Vue rulează pe **Windows** și se conectează la backend-ul din WSL.

## Modificări în proiect

| Fișier | Modificare |
|---|---|
| `backend/build.gradle.kts` | `languageVersion` 23 → **21** (pe Ubuntu, în apt există doar OpenJDK 21) |
| `backend/settings.gradle.kts` | plugin `foojay-resolver-convention` – dacă JDK 21 nu e instalat (ex. pe Windows), Gradle îl descarcă automat |
| `frontend/vite.config.js` | adresa backend-ului se citește din `BACKEND_URL` (implicit `http://localhost:8080`), deci nu mai trebuie editat fișierul când se schimbă IP-ul WSL |
| `scripts/bootstrap-gradle.sh` | marcat executabil în git (`chmod +x`) |

Codul nu folosea funcții din Java 22/23, deci a compilat direct cu Java 21 (bytecode versiunea 65). Toate cele 50 de teste trec.

## Pași

### 1. WSL + Ubuntu (PowerShell ca Administrator)

```powershell
wsl --install            # alege Ubuntu, apoi restart
```

În Ubuntu (după ce creezi utilizatorul și parola):

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install git openjdk-21-jdk unzip -y
git --version
java -version
```

### 2. PostgreSQL

```bash
sudo apt install postgresql postgresql-contrib -y
sudo service postgresql start
sudo service postgresql status
sudo -u postgres psql
```

```sql
CREATE DATABASE instakill;
CREATE USER instakill WITH PASSWORD 'instakill_password';
GRANT ALL PRIVILEGES ON DATABASE instakill TO instakill;
\c instakill
GRANT ALL ON SCHEMA public TO instakill;
ALTER SCHEMA public OWNER TO instakill;
\q
```

```bash
psql -h localhost -U instakill -d instakill     # test login
```

### 3. Clonare și build

```bash
cd ~
git clone https://github.com/sabina200329/ACS_JAVA_REPO.git
ls -al
cd ACS_JAVA_REPO/instakill-*/instakill-*
./scripts/bootstrap-gradle.sh
cd backend
chmod +x gradlew
./gradlew build
ls -l build/libs        # instakill-0.1.0-SNAPSHOT.jar
```

### 4. Pornire backend (cu PostgreSQL din WSL)

```bash
./gradlew bootRun --args='--spring.profiles.active=postgres'
```

Verificare: `curl http://localhost:8080/api/health` (din WSL) sau http://localhost:8080/api/health din browserul de pe Windows.

### 5. Frontend pe Windows, conectat la WSL

```powershell
wsl hostname -I                                  # ex. 172.x.x.x
cd frontend
$env:BACKEND_URL = "http://<IP-WSL>:8080"
npm install
npm run dev
```

Se deschide http://localhost:5173, login cu `alice` / `password`.

## Dovezi

| Build reușit în Linux | Backend pornit |
|---|---|
| ![](1_build.png) | ![](2_bootrun.png) |
| **Frontend pe Windows conectat la WSL** | **Health check** |
| ![](3_frontend.png) | ![](4_health.png) |

## Reflecție

_(de completat după rulare)_
