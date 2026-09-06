from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import time
import os

from models import TranslateRequest, TranslateResponse, HealthResponse, DetectLanguageRequest, DetectLanguageResponse
from cache import server_cache
from translator import translator

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Warm up translation model on startup
    translator.warmup()
    yield

app = FastAPI(
    title="Vynce Translation Service (IndicTrans2)",
    description="Dedicated server-side neural machine translation for Indic and English song lyrics",
    version="2.0.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health", response_model=HealthResponse)
def health_check():
    return HealthResponse(
        status="ok" if translator.is_ready else "warming_up",
        process_alive=True,
        model_ready=translator.is_ready,
        provider="indictrans2",
        model_name="ai4bharat/indictrans2-directional-router",
        device=translator.device
    )

@app.get("/api/health", response_model=HealthResponse)
def api_health_check():
    return health_check()

@app.post("/translate", response_model=TranslateResponse)
def translate_lyrics(req: TranslateRequest):
    route = translator.get_route(req.sourceLanguage, req.targetLanguage)
    model_name = translator.get_model_name_for_route(route)
    mode = req.mode or "literal"

    if not req.lines:
        return TranslateResponse(
            translatedLines=[],
            sourceLanguage=req.sourceLanguage,
            targetLanguage=req.targetLanguage,
            mode=mode,
            cached=False,
            provenance={
                "provider": "indictrans2",
                "model": model_name,
                "route": route,
                "sourceLanguage": req.sourceLanguage,
                "targetLanguage": req.targetLanguage,
                "mode": mode,
                "createdAt": int(time.time() * 1000)
            }
        )

    content_hash = req.contentHash or str(hash("".join(req.lines) + req.sourceLanguage + req.targetLanguage + mode))

    # 1. Check Server SQLite Cache (Tier 2)
    cached = server_cache.get_cached(content_hash, req.sourceLanguage, req.targetLanguage, mode)
    if cached:
        return TranslateResponse(
            translatedLines=cached["translated_lines"],
            sourceLanguage=req.sourceLanguage,
            targetLanguage=req.targetLanguage,
            mode=mode,
            cached=True,
            provenance={
                "provider": cached["provider"],
                "model": cached["model"],
                "route": route,
                "sourceLanguage": req.sourceLanguage,
                "targetLanguage": req.targetLanguage,
                "mode": mode,
                "createdAt": cached["created_at"]
            }
        )

    # 2. Run Directional IndicTrans2 Inference
    translated = translator.translate(req.lines, req.sourceLanguage, req.targetLanguage)

    # 3. Store in Server SQLite Cache (Tier 2)
    server_cache.set_cached(
        content_hash=content_hash,
        source_lang=req.sourceLanguage,
        target_lang=req.targetLanguage,
        mode=mode,
        translated_lines=translated,
        provider="indictrans2",
        model=model_name
    )

    return TranslateResponse(
        translatedLines=translated,
        sourceLanguage=req.sourceLanguage,
        targetLanguage=req.targetLanguage,
        mode=mode,
        cached=False,
        provenance={
            "provider": "indictrans2",
            "model": model_name,
            "route": route,
            "sourceLanguage": req.sourceLanguage,
            "targetLanguage": req.targetLanguage,
            "mode": mode,
            "createdAt": int(time.time() * 1000)
        }
    )

@app.post("/api/translate", response_model=TranslateResponse)
def api_translate_lyrics(req: TranslateRequest):
    return translate_lyrics(req)

@app.get("/languages")
@app.get("/api/languages")
def get_languages():
    return [
        {"code": "hin_Deva", "name": "Hindi", "native": "हिन्दी"},
        {"code": "eng_Latn", "name": "English", "native": "English"},
        {"code": "tel_Telu", "name": "Telugu", "native": "తెలుగు"},
        {"code": "tam_Taml", "name": "Tamil", "native": "தமிழ்"},
        {"code": "pan_Guru", "name": "Punjabi", "native": "ਪੰਜਾਬੀ"},
        {"code": "ben_Beng", "name": "Bengali", "native": "বাংলা"},
        {"code": "mar_Deva", "name": "Marathi", "native": "मराठी"},
        {"code": "kan_Knda", "name": "Kannada", "native": "ಕನ್ನಡ"},
        {"code": "mal_Mlym", "name": "Malayalam", "native": "മലയാളം"},
        {"code": "guj_Gujr", "name": "Gujarati", "native": "ગુજરાતી"},
        {"code": "ori_Orya", "name": "Odia", "native": "ଓଡ଼ਿଆ"},
        {"code": "urd_Arab", "name": "Urdu", "native": "اردو"},
    ]

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
