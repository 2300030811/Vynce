from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any

class TranslateRequest(BaseModel):
    songId: Optional[str] = None
    contentHash: Optional[str] = None
    lines: List[str] = Field(..., description="Array of pure lyric text lines to translate")
    sourceLanguage: str = Field(..., description="Source language code e.g. hin_Deva, tel_Telu")
    targetLanguage: str = Field(..., description="Target language code e.g. eng_Latn, hin_Deva")
    mode: Optional[str] = Field("literal", description="Translation mode: literal (faithful NMT) or lyrical")

class TranslateResponse(BaseModel):
    translatedLines: List[str]
    sourceLanguage: str
    targetLanguage: str
    mode: str
    cached: bool
    provenance: Dict[str, Any]

class DetectLanguageRequest(BaseModel):
    text: str

class DetectLanguageResponse(BaseModel):
    script: str
    language: str
    code: str
    confidence: float
    isRomanized: bool
    isAmbiguous: bool

class HealthResponse(BaseModel):
    status: str
    process_alive: bool
    model_ready: bool
    provider: str
    model_name: str
    device: str
