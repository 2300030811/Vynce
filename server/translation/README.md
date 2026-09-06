# Vynce IndicTrans2 Translation Microservice

Dedicated neural machine translation microservice powered by **AI4Bharat IndicTrans2** supporting all 22 scheduled Indian languages and English with directional model routing, contextual stanza preservation, and persistent two-tier SQLite caching.

---

## 1. Directional Model Routing Architecture

IndicTrans2 uses three distinct model checkpoints tailored for optimal translation quality across translation directions:

```text
                               TRANSLATION REQUEST
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
           [ Indic → English ]  [ Indic → Indic ]   [ English → Indic ]
                    │                   │                   │
                    ▼                   ▼                   ▼
           indictrans2-indic-en indictrans2-indic-indic indictrans2-en-indic
                 (1B / 200M)         (1B / 320M)         (1B / 200M)
```

- **Indic $\to$ English**: `ai4bharat/indictrans2-indic-en-1B` (or distilled 200M variant for CPU)
- **Indic $\to$ Indic**: `ai4bharat/indictrans2-indic-indic-1B` (or distilled 320M variant for CPU)
- **English $\to$ Indic**: `ai4bharat/indictrans2-en-indic-1B` (or distilled 200M variant for CPU)

---

## 2. Hugging Face Gated Model Access Setup

AI4Bharat IndicTrans2 model repositories are listed under the **MIT License**, but Hugging Face requires accepting the model access terms:

1. Create a free account on [Hugging Face](https://huggingface.co).
2. Visit the model pages and accept the terms:
   - [ai4bharat/indictrans2-indic-en-1B](https://huggingface.co/ai4bharat/indictrans2-indic-en-1B)
   - [ai4bharat/indictrans2-indic-indic-1B](https://huggingface.co/ai4bharat/indictrans2-indic-indic-1B)
   - [ai4bharat/indictrans2-en-indic-1B](https://huggingface.co/ai4bharat/indictrans2-en-indic-1B)
3. Authenticate your environment:
   ```bash
   huggingface-cli login
   # or set your environment variable:
   export HF_TOKEN="hf_xxxxxxxxxxxxxxxxxxxxxx"
   ```

---

## 3. Quick Start & Execution

### Setup Python Environment
```bash
cd server/translation
python -m venv venv

# On Windows:
.\venv\Scripts\activate
# On Linux/macOS:
source venv/bin/activate

pip install -r requirements.txt
```

### Start the Service
```bash
python app.py
# Or with uvicorn directly:
uvicorn app:app --host 0.0.0.0 --port 8000
```

---

## 4. Production Deployment & Architecture

In production, the web client should never expose the internal model service directly:

```text
Browser / Web App
       │
       ▼
Reverse Proxy / Production API (/api/translate)
       │
       ▼
Translation Service (:8000)
   ├── Tier-2 SQLite Cache (translation_cache.db)
   └── IndicTrans2 Directional Router
```

### Docker Deployment
```bash
docker build -t vynce-translation-service .
docker run -d -p 8000:8000 --gpus all --name vynce-translator vynce-translation-service
```
