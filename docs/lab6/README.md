# Lab 6 – Load testing Instakill cu Locust (SQLite vs PostgreSQL)

## Mediul de test

| | |
|---|---|
| Aplicație | Instakill (Spring Boot 3.3, Java 23), un singur server, rulat local (`localhost:8080`) |
| Date | generate cu `scripts/populate_db.py`: **10.003 utilizatori, ~150.000 postări, ~750.000 comentarii, ~1.500.000 like-uri** (aceeași cantitate în ambele baze de date) |
| Unealtă | Locust 2.47, `locustfile.py` din arhiva temei (login, feed, analytics, postări, like-uri, comentarii; pauză 1–5 s între acțiuni) |
| Rulare | fiecare test 60 s, toți utilizatorii porniți deodată (`spawn-rate = users`), notificările ntfy.sh dezactivate pe durata testelor |
| Baze de date | **SQLite** (fișierul `instakill.db`) și **PostgreSQL 17** (profilul Spring `postgres`) |

Pornire:

```bash
# SQLite (implicit)
./gradlew bootRun
# PostgreSQL
./gradlew bootRun --args='--spring.profiles.active=postgres'

# Locust
locust -f locustfile.py --host=http://localhost:8080        # interfața web pe http://localhost:8089
```

Populare PostgreSQL (scriptul Python adaptat, pachetul `psycopg2-binary`):

```bash
python scripts/populate_db.py --pg postgresql://instakill:instakill_password@localhost:5432/instakill
```

---

## Partea 1 – SQLite

| Utilizatori | Cereri | Erori | Timp mediu | Median | 95% | Max | RPS | Feed (medie) | Analytics | Like |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1  | 21  | **0** | 581 ms   | 260 ms  | 1,3 s | 2,0 s  | 0,38 | 1.276 ms  | 78 ms    | 269 ms   |
| 2  | 41  | **0** | 765 ms   | 1,2 s   | 1,6 s | 1,6 s  | 0,69 | 1.368 ms  | 77 ms    | 269 ms   |
| 5  | 90  | **0** | 1.035 ms | 1,3 s   | 2,6 s | 2,9 s  | 1,55 | 1.792 ms  | 116 ms   | 510 ms   |
| 10 | 131 | **6** | 2.293 ms | 2,4 s   | 5,2 s | 6,1 s  | 2,24 | 3.916 ms  | 171 ms   | 798 ms   |
| 50 | 166 | **2** | 9.767 ms | 1,7 s   | 28 s  | 31,5 s | 2,80 | 25.868 ms | 1.275 ms | 2.854 ms |

Observații:

- **1 utilizator: 0 erori**, toate cererile reușesc. Totuși feed-ul durează ~1,3 s chiar și fără concurență.
- **Degradarea începe de la ~5 utilizatori**: timpul mediu crește (0,6 s → 1 s → 2,3 s), iar 95% din cereri ajung la 2,6 s.
- **Primele erori apar la 10 utilizatori** (6 × HTTP 500 la `likes/toggle`). În log-ul serverului: `[SQLITE_BUSY] The database file is locked`.
- **La 50 de utilizatori serverul e saturat**: debitul abia crește (2,2 → 2,8 cereri/s) în timp ce timpul de răspuns crește de ~4 ori; feed-ul ajunge la **26 s**, iar maximul la 31 s. Cererile stau la coadă.
- Pragul de degradare: **~5 utilizatori concurenți** (latență), **~10 utilizatori** (erori).

Capturi (Statistics / Charts): [`sqlite/`](sqlite/)

| 10 utilizatori | 50 utilizatori |
|---|---|
| ![](sqlite/u10_statistics.png) | ![](sqlite/u50_statistics.png) |
| ![](sqlite/u10_charts.png) | ![](sqlite/u50_charts.png) |

---

## Partea 2 – PostgreSQL

Modificări făcute:

- driverul `org.postgresql:postgresql` în `build.gradle.kts`;
- profilul `application-postgres.yaml` (URL, utilizator `instakill`, parolă `instakill_password`, dialect PostgreSQL); SQLite rămâne implicit;
- interogările de analytics (Lab 5) au acum variantă pentru fiecare bază de date (`AnalyticsSql`): SQLite stochează datele ca epoch în ms (`date(created_at/1000, 'unixepoch')`), PostgreSQL ca `timestamp with time zone` (`date_trunc('week', created_at)`);
- `populate_db.py` primește `--pg <URL>` și scrie în PostgreSQL cu `psycopg2` (`execute_values`, o singură tranzacție).

| Utilizatori | Cereri | Erori | Timp mediu | Median | 95% | Max | RPS | Feed (medie) | Analytics | Like |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1  | 25   | **0** | 57 ms | 40 ms | 150 ms | 287 ms | 0,43  | 50 ms | 108 ms | 32 ms |
| 2  | 54   | **0** | 26 ms | 20 ms | 74 ms  | 93 ms  | 0,92  | 19 ms | 73 ms  | 9 ms  |
| 5  | 128  | **0** | 24 ms | 14 ms | 87 ms  | 95 ms  | 2,17  | 14 ms | 73 ms  | 8 ms  |
| 10 | 275  | **0** | 25 ms | 14 ms | 97 ms  | 132 ms | 4,72  | 17 ms | 73 ms  | 8 ms  |
| 50 | 1321 | **1** | 38 ms | 11 ms | 220 ms | 570 ms | 22,23 | 22 ms | 65 ms  | 7 ms  |

Capturi: [`postgres/`](postgres/)

| 10 utilizatori | 50 utilizatori |
|---|---|
| ![](postgres/u10_statistics.png) | ![](postgres/u50_statistics.png) |
| ![](postgres/u10_charts.png) | ![](postgres/u50_charts.png) |

### Comparație directă

| | SQLite | PostgreSQL |
|---|---|---|
| Timp mediu la 10 utilizatori | 2.293 ms | **25 ms** (~90× mai rapid) |
| Timp mediu la 50 utilizatori | 9.767 ms | **38 ms** |
| 95% la 50 utilizatori | 28 s | **220 ms** |
| Debit la 50 utilizatori | 2,8 cereri/s | **22,2 cereri/s** (~8×) |
| Erori la 10 / 50 utilizatori | 6 / 2 | **0 / 1** |
| Populare cu `populate_db.py` | 39 s | 162 s |

---

## Experiment suplimentar: SQLite cu index pe `likes`

Feed-ul SQLite era lent chiar și cu un singur utilizator. Cauza: entitatea `LikeEntity` declară indexul unic `idx_likes_post_user (post_id, user_id)`, dar tabelul `likes` din fișierul SQLite fusese creat înainte, iar `ddl-auto: update` nu a mai adăugat indexul. Pentru fiecare postare din feed, aplicația verifică „a dat utilizatorul like?” (`existsByPostIdAndUserId`) → **scanare completă a 1,5 milioane de rânduri × 10 postări**. În PostgreSQL schema a fost creată de la zero, deci indexul există.

Ca să separ efectul indexului de efectul bazei de date, am adăugat indexul pe o copie a bazei SQLite și am repetat testele:

| Utilizatori | Erori | Timp mediu | 95% | Max | RPS | Feed | Scrieri (medie) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1  | 0      | 32 ms | 60 ms  | 275 ms   | 0,47  | 24 ms | 18 ms |
| 10 | 0      | 29 ms | 110 ms | 246 ms   | 4,54  | 13 ms | 17 ms |
| 50 | **10** | 69 ms | 400 ms | 1.082 ms | 21,95 | 49 ms | 40 ms |

Capturi: [`sqlite-with-index/`](sqlite-with-index/)

---

## Analiză

**Cum scalează Instakill.** Cu SQLite (așa cum era aplicația), performanța se degradează de la ~5 utilizatori concurenți și apar erori de la ~10; la 50 de utilizatori serverul e saturat (debitul stagnează la ~3 cereri/s, latențele ajung la zeci de secunde). Cu PostgreSQL, timpii rămân sub 100 ms pentru 95% din cereri până la 10 utilizatori și sub 220 ms la 50, iar debitul crește aproape liniar cu numărul de utilizatori (0,4 → 22 cereri/s). La 50 de utilizatori debitul PostgreSQL e limitat de Locust (pauza de 1–5 s între acțiuni), nu de server – limita lui nu a fost atinsă în aceste teste.

**De ce e PostgreSQL mai bun – două cauze distincte:**

1. **Indexul lipsă (cauza principală a latenței).** Experimentul cu index arată că SQLite cu index are latențe apropiate de PostgreSQL la 1–10 utilizatori (feed 13–24 ms în loc de 1,3–3,9 s). Deci o mare parte din diferența din Partea 1 nu vine din motorul bazei de date, ci dintr-o schemă incompletă. Lecție: performanța depinde mai mult de indexuri și de interogări decât de alegerea bazei de date, la volum mic.
2. **Concurența la scriere (cauza erorilor).** SQLite permite **un singur scriitor la un moment dat** (blocare la nivel de fișier). Chiar și cu index, la 50 de utilizatori apar 10 erori `SQLITE_BUSY` la like-uri, comentarii și postări noi, iar coada de așteptare crește timpul maxim la ~1 s. PostgreSQL are **MVCC și blocare la nivel de rând**: mai mulți utilizatori scriu simultan fără să se blocheze reciproc, iar cititorii nu așteaptă după scriitori. De aceea erorile dispar și latența rămâne stabilă.

**Singura eroare PostgreSQL** (50 de utilizatori) este `duplicate key value violates unique constraint "idx_likes_post_user"`. Nu e o problemă de performanță, ci o **condiție de cursă** în aplicație: Locust folosește doar conturile `alice` și `bob` pentru toți utilizatorii simulați, iar două cereri „toggle like” pentru același utilizator și aceeași postare au ajuns simultan. Ambele au văzut „nu există like” și au încercat să-l insereze; constrângerea unică a protejat datele. O soluție ar fi tratarea excepției (sau `INSERT ... ON CONFLICT DO NOTHING`).

**Unde PostgreSQL e mai lent:** popularea bazei (162 s față de 39 s) – fiecare inserare actualizează indexurile (inclusiv indexul unic pe `likes`) și trece prin rețea (TCP, chiar și local), pe când SQLite scrie direct în fișier. Prima cerere este puțin mai lentă (încălzirea conexiunilor și a cache-ului PostgreSQL).

**CPU / memorie:** nu au fost măsurate separat; toate testele au rulat pe același laptop (aplicație, baza de date și Locust împreună), deci rezultatele sunt comparabile între ele, dar nu reprezintă un mediu de producție.

**Alte blocaje observate (pentru îmbunătățiri viitoare):**

- feed-ul face N+1 interogări (autorul și „liked by me” pentru fiecare postare) – ar putea fi o singură interogare cu JOIN;
- sortarea „trending” încarcă postările în memorie și le sortează în Java;
- fișierul SQLite existent nu primește automat indexurile noi – schema ar trebui gestionată cu migrări (Flyway/Liquibase), nu cu `ddl-auto: update`.

**Concluzie.** Pentru un singur utilizator, SQLite (cu indexurile corecte) e suficient și simplu. Pentru o aplicație cu mai mulți utilizatori care scriu simultan, PostgreSQL scalează mult mai bine: fără erori de blocare, latență stabilă și debit care crește cu încărcarea.
