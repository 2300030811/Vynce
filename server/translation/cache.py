import sqlite3
import json
import time
import os
from typing import Optional, List, Dict, Any

DB_PATH = os.path.join(os.path.dirname(__file__), 'translation_cache.db')

class ServerTranslationCache:
    def __init__(self, db_path: str = DB_PATH):
        self.db_path = db_path
        self._init_db()

    def _get_connection(self):
        return sqlite3.connect(self.db_path)

    def _init_db(self):
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS translation_cache (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    content_hash TEXT NOT NULL,
                    source_lang TEXT NOT NULL,
                    target_lang TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    translated_lines TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    model TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    UNIQUE(content_hash, source_lang, target_lang, mode)
                )
            ''')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_cache_lookup ON translation_cache(content_hash, source_lang, target_lang, mode)')
            conn.commit()

    def get_cached(self, content_hash: str, source_lang: str, target_lang: str, mode: str) -> Optional[Dict[str, Any]]:
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                SELECT translated_lines, provider, model, created_at
                FROM translation_cache
                WHERE content_hash = ? AND source_lang = ? AND target_lang = ? AND mode = ?
            ''', (content_hash, source_lang, target_lang, mode))
            row = cursor.fetchone()
            if row:
                return {
                    'translated_lines': json.loads(row[0]),
                    'provider': row[1],
                    'model': row[2],
                    'created_at': row[3]
                }
        return None

    def set_cached(self, content_hash: str, source_lang: str, target_lang: str, mode: str, translated_lines: List[str], provider: str, model: str):
        with self._get_connection() as conn:
            cursor = conn.cursor()
            cursor.execute('''
                INSERT OR REPLACE INTO translation_cache 
                (content_hash, source_lang, target_lang, mode, translated_lines, provider, model, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                content_hash,
                source_lang,
                target_lang,
                mode,
                json.dumps(translated_lines),
                provider,
                model,
                int(time.time() * 1000)
            ))
            conn.commit()

# Singleton cache instance
server_cache = ServerTranslationCache()
