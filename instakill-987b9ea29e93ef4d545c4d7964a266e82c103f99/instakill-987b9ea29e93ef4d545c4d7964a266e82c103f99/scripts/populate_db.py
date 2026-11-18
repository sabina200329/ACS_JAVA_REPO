#!/usr/bin/env python3
"""
Populează baza de date SQLite a Instakill cu date de test (Lab 5, partea 2).

Ce face:
  1. Șterge toate comentariile, like-urile și postările, plus toți utilizatorii
     în afară de `alice` și `bob`.
  2. Adaugă utilizatorul `charlie` cu parola `password` (hash bcrypt).
  3. Generează N utilizatori (implicit 10.000) cu nume unice (faker).
  4. Fiecare utilizator (inclusiv alice, bob, charlie) primește 10–20 postări,
     create în ultima săptămână, cu 0–10 comentarii și 0–20 like-uri fiecare.

Integritatea datelor: toate cheile (author_id, post_id, user_id) referă rânduri
existente, un utilizator dă cel mult un like pe o postare, iar contoarele
posts.like_count / posts.comment_count corespund rândurilor inserate.

Performanță: totul rulează într-o singură tranzacție, cu inserări în loturi
(executemany), nu rând cu rând.

Instalare:   pip install faker bcrypt
Rulare:      python populate_db.py --db ../instakill.db
             python populate_db.py --db ../instakill.db --users 500 --seed 42

Oprește aplicația Spring înainte de rulare, ca baza de date să nu fie blocată.
"""

from __future__ import annotations

import argparse
import random
import sqlite3
import sys
import time
import uuid
from datetime import datetime, timedelta
from pathlib import Path

# Diacriticele se afișează corect și în consola Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

try:
    import bcrypt
    from faker import Faker
except ImportError:
    sys.exit("Lipsesc pachetele necesare. Rulează: pip install faker bcrypt")

KEEP_USERS = ("alice", "bob")
DEFAULT_PASSWORD = "password"

PHOTO_URL = "https://picsum.photos/seed/{seed}/600/400"
VIDEO_URLS = (
    "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
    "https://www.w3schools.com/html/mov_bbb.mp4",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Populează instakill.db cu date de test.")
    parser.add_argument("--db", default="instakill.db",
                        help="calea către fișierul SQLite (implicit: ./instakill.db)")
    parser.add_argument("--users", type=int, default=10_000,
                        help="câți utilizatori noi să genereze (implicit: 10000)")
    parser.add_argument("--min-posts", type=int, default=10, help="minim postări per utilizator (10)")
    parser.add_argument("--max-posts", type=int, default=20, help="maxim postări per utilizator (20)")
    parser.add_argument("--max-comments", type=int, default=10, help="maxim comentarii per postare (10)")
    parser.add_argument("--max-likes", type=int, default=20, help="maxim like-uri per postare (20)")
    parser.add_argument("--days", type=int, default=7, help="postările sunt din ultimele N zile (7)")
    parser.add_argument("--batch-size", type=int, default=5_000, help="rânduri per lot de inserare (5000)")
    parser.add_argument("--seed", type=int, default=None, help="seed pentru rezultate reproductibile")
    args = parser.parse_args()

    if args.users < 0 or args.min_posts < 0 or args.max_posts < args.min_posts:
        parser.error("valori invalide pentru --users / --min-posts / --max-posts")
    if args.max_comments < 0 or args.max_likes < 0 or args.batch_size <= 0 or args.days <= 0:
        parser.error("valori invalide pentru --max-comments / --max-likes / --batch-size / --days")
    return args


def to_millis(dt: datetime) -> int:
    """Aplicația (Hibernate + SQLite) stochează Instant ca epoch în milisecunde."""
    return int(dt.timestamp() * 1000)


def bcrypt_hash(password: str) -> str:
    hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()
    # Spring BCryptPasswordEncoder generează prefixul $2a$; e același algoritm.
    return hashed.replace("$2b$", "$2a$", 1)


class BatchWriter:
    """Adună rânduri și le inserează cu executemany când lotul e plin."""

    def __init__(self, conn: sqlite3.Connection, sql: str, batch_size: int):
        self.conn = conn
        self.sql = sql
        self.batch_size = batch_size
        self.rows: list[tuple] = []
        self.total = 0

    def add(self, row: tuple) -> None:
        self.rows.append(row)
        if len(self.rows) >= self.batch_size:
            self.flush()

    def flush(self) -> None:
        if self.rows:
            self.conn.executemany(self.sql, self.rows)
            self.total += len(self.rows)
            self.rows.clear()


def check_schema(conn: sqlite3.Connection) -> None:
    tables = {row[0] for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
    missing = {"users", "posts", "likes", "comments"} - tables
    if missing:
        sys.exit(f"Tabele lipsă: {sorted(missing)}. Pornește o dată aplicația ca să creeze schema.")


def clear_data(conn: sqlite3.Connection) -> None:
    conn.execute("DELETE FROM comments")
    conn.execute("DELETE FROM likes")
    conn.execute("DELETE FROM posts")
    placeholders = ",".join("?" for _ in KEEP_USERS)
    conn.execute(f"DELETE FROM users WHERE username NOT IN ({placeholders})", KEEP_USERS)


def unique_username(fake: Faker, taken: set[str]) -> str:
    base = fake.user_name().lower()
    name = base
    while name in taken:
        name = f"{base}{random.randint(1, 999_999)}"
    taken.add(name)
    return name


def create_users(conn, fake: Faker, count: int, now: datetime, batch_size: int) -> list[str]:
    """Returnează lista cu ID-urile tuturor utilizatorilor (vechi + noi)."""
    existing = conn.execute("SELECT id, username FROM users").fetchall()
    user_ids = [row[0] for row in existing]
    taken = {row[1] for row in existing}

    writer = BatchWriter(conn,
                         "INSERT INTO users (id, created_at, email, password_hash, username) "
                         "VALUES (?, ?, ?, ?, ?)",
                         batch_size)

    # charlie / password, cu hash bcrypt propriu
    charlie_id = str(uuid.uuid4())
    taken.add("charlie")
    writer.add((charlie_id, to_millis(now - timedelta(days=30)), "charlie@example.com",
                bcrypt_hash(DEFAULT_PASSWORD), "charlie"))
    user_ids.append(charlie_id)

    # bcrypt e lent intenționat (~0.1s/hash); folosim un singur hash pentru utilizatorii generați
    shared_hash = bcrypt_hash(DEFAULT_PASSWORD)
    for _ in range(count):
        user_id = str(uuid.uuid4())
        username = unique_username(fake, taken)
        created = fake.date_time_between(start_date="-90d", end_date="-8d")
        writer.add((user_id, to_millis(created), f"{username}@example.com", shared_hash, username))
        user_ids.append(user_id)

    writer.flush()
    return user_ids


def create_content(conn, fake: Faker, user_ids: list[str], args, now: datetime) -> dict[str, int]:
    week_ago = now - timedelta(days=args.days)
    now_ms = to_millis(now)

    # Texte generate cu faker, dintr-un set prealocat (mult mai rapid decât un apel per rând)
    post_texts = [" ".join(fake.words(nb=random.randint(1, 10))).capitalize() for _ in range(5_000)]
    comment_texts = [fake.sentence(nb_words=random.randint(2, 12))[:500] for _ in range(5_000)]

    posts = BatchWriter(conn,
                        "INSERT INTO posts (id, author_id, comment_count, content, created_at, image_url, "
                        "like_count, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        args.batch_size)
    comments = BatchWriter(conn,
                           "INSERT INTO comments (id, author_id, content, created_at, post_id) "
                           "VALUES (?, ?, ?, ?, ?)",
                           args.batch_size)
    likes = BatchWriter(conn,
                        "INSERT INTO likes (id, created_at, post_id, user_id) VALUES (?, ?, ?, ?)",
                        args.batch_size)

    max_likes = min(args.max_likes, len(user_ids))
    for index, author_id in enumerate(user_ids, start=1):
        for _ in range(random.randint(args.min_posts, args.max_posts)):
            post_id = str(uuid.uuid4())
            created_ms = to_millis(fake.date_time_between_dates(datetime_start=week_ago, datetime_end=now))

            if random.random() < 0.5:
                image_url = PHOTO_URL.format(seed=post_id[:8])
            else:
                image_url = random.choice(VIDEO_URLS)

            comment_count = random.randint(0, args.max_comments)
            for _ in range(comment_count):
                comments.add((str(uuid.uuid4()), random.choice(user_ids), random.choice(comment_texts),
                              random.randint(created_ms, now_ms), post_id))

            like_count = random.randint(0, max_likes)
            # random.sample => utilizatori distincți, deci fără like-uri duplicate
            for liker_id in random.sample(user_ids, like_count):
                likes.add((str(uuid.uuid4()), random.randint(created_ms, now_ms), post_id, liker_id))

            updated_ms = created_ms if comment_count == 0 and like_count == 0 else now_ms
            posts.add((post_id, author_id, comment_count, random.choice(post_texts), created_ms,
                       image_url, like_count, updated_ms))

        if index % 1_000 == 0:
            print(f"  ... {index}/{len(user_ids)} utilizatori procesați")

    for writer in (posts, comments, likes):
        writer.flush()
    return {"posts": posts.total, "comments": comments.total, "likes": likes.total}


def verify_integrity(conn: sqlite3.Connection) -> None:
    checks = {
        "postări fără autor": "SELECT COUNT(*) FROM posts p LEFT JOIN users u ON u.id = p.author_id "
                              "WHERE u.id IS NULL",
        "comentarii orfane": "SELECT COUNT(*) FROM comments c LEFT JOIN posts p ON p.id = c.post_id "
                             "LEFT JOIN users u ON u.id = c.author_id WHERE p.id IS NULL OR u.id IS NULL",
        "like-uri orfane": "SELECT COUNT(*) FROM likes l LEFT JOIN posts p ON p.id = l.post_id "
                           "LEFT JOIN users u ON u.id = l.user_id WHERE p.id IS NULL OR u.id IS NULL",
        "like-uri duplicate": "SELECT COUNT(*) FROM (SELECT 1 FROM likes GROUP BY post_id, user_id "
                              "HAVING COUNT(*) > 1)",
    }
    for label, sql in checks.items():
        bad = conn.execute(sql).fetchone()[0]
        if bad:
            raise RuntimeError(f"Verificare eșuată: {bad} {label}")


def main() -> None:
    args = parse_args()
    db_path = Path(args.db)
    if not db_path.exists():
        sys.exit(f"Nu găsesc baza de date: {db_path.resolve()}. Pornește o dată aplicația sau dă --db.")

    if args.seed is not None:
        random.seed(args.seed)
        Faker.seed(args.seed)
    fake = Faker()
    now = datetime.now()

    started = time.perf_counter()
    conn = sqlite3.connect(db_path, isolation_level=None)  # tranzacția o gestionăm manual
    try:
        check_schema(conn)
        conn.execute("PRAGMA foreign_keys = ON")
        conn.execute("BEGIN")
        try:
            print("Șterg datele vechi (păstrez alice și bob)...")
            clear_data(conn)

            print(f"Creez charlie + {args.users} utilizatori...")
            user_ids = create_users(conn, fake, args.users, now, args.batch_size)

            print(f"Creez postări, comentarii și like-uri pentru {len(user_ids)} utilizatori...")
            totals = create_content(conn, fake, user_ids, args, now)

            print("Verific integritatea datelor...")
            verify_integrity(conn)
            conn.execute("COMMIT")
        except BaseException:
            conn.execute("ROLLBACK")
            print("Eroare — toate modificările au fost anulate (ROLLBACK).")
            raise

        elapsed = time.perf_counter() - started
        print(f"Gata în {elapsed:.1f}s: {len(user_ids)} utilizatori, {totals['posts']} postări, "
              f"{totals['comments']} comentarii, {totals['likes']} like-uri.")
        print("Login de test: charlie / password")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
