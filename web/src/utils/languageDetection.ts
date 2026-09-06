/**
 * Multi-Language Script & Language Detection Engine
 * 
 * Implements two-stage detection:
 * Stage 1: Deterministic Script Classification (Devanagari, Telugu, Tamil, Gurmukhi, Latin, etc.)
 * Stage 2: Heuristic / Probabilistic Language Inference with Confidence Scoring
 */

import { DetectedLanguage } from '../types/music';

// Official AI4Bharat IndicTrans2 Language Codes
export const SUPPORTED_LANGUAGES = [
  { code: 'hin_Deva', name: 'Hindi', native: 'हिन्दी', script: 'Devanagari' },
  { code: 'eng_Latn', name: 'English', native: 'English', script: 'Latin' },
  { code: 'tel_Telu', name: 'Telugu', native: 'తెలుగు', script: 'Telugu' },
  { code: 'tam_Taml', name: 'Tamil', native: 'தமிழ்', script: 'Tamil' },
  { code: 'pan_Guru', name: 'Punjabi', native: 'ਪੰਜਾਬੀ', script: 'Gurmukhi' },
  { code: 'ben_Beng', name: 'Bengali', native: 'বাংলা', script: 'Bengali' },
  { code: 'mar_Deva', name: 'Marathi', native: 'मराठी', script: 'Devanagari' },
  { code: 'kan_Knda', name: 'Kannada', native: 'ಕನ್ನಡ', script: 'Kannada' },
  { code: 'mal_Mlym', name: 'Malayalam', native: 'മലയാളം', script: 'Malayalam' },
  { code: 'guj_Gujr', name: 'Gujarati', native: 'ગુજરાતી', script: 'Gujarati' },
  { code: 'ori_Orya', name: 'Odia', native: 'ଓଡ଼ିଆ', script: 'Odia' },
  { code: 'urd_Arab', name: 'Urdu', native: 'اردو', script: 'Perso-Arabic' },
];

const ROMANIZED_INDIC_LEXICON: Record<string, string[]> = {
  hin_Deva: [
    'dil', 'pyar', 'pyaar', 'mera', 'meri', 'mere', 'tera', 'teri', 'tere',
    'hai', 'hain', 'ho', 'hoon', 'hum', 'tum', 'main', 'mujhe', 'tujhe',
    'zindagi', 'ishq', 'mohabbat', 'sanam', 'deewana', 'pagal', 'raat', 'baat',
    'chupke', 'aankhen', 'nazar', 'saath', 'kabhi', 'nahi', 'nahin', 'kyun', 'kaise',
  ],
  tel_Telu: [
    'ninnu', 'nenu', 'nuvvu', 'prema', 'pranam', 'gunde', 'kalisi', 'manasu',
    'choodu', 'cheliya', 'chitti', 'raa', 'unnava', 'kosam', 'valapula',
  ],
  tam_Taml: [
    'uyire', 'enakku', 'unnai', 'kaadhal', 'kannamma', 'vizhi', 'vaada', 'thalaiva',
    'aiyo', 'paatha', 'nenjukkul', 'anbe', 'azhage',
  ],
  pan_Guru: [
    'mainu', 'tainu', 'kudi', 'mundeya', 'gaddi', 'sohni', 'nachna', 'pind',
    'jatt', 'yaar', 'rabb', 'tere', 'karke', 'akh', 'chann',
  ],
};

const ENGLISH_COMMON_WORDS = new Set([
  'the', 'and', 'you', 'i', 'to', 'a', 'in', 'that', 'it', 'my', 'is', 'me', 'of',
  'for', 'your', 'with', 'on', 'all', 'we', 'love', 'baby', 'night', 'day', 'feel',
  'heart', 'never', 'just', 'girl', 'boy', 'know', 'say', 'see', 'time', 'like',
]);

/**
 * Detects the dominant writing script of a text sample
 */
export function detectScript(text: string): string {
  if (!text) return 'Latin';

  const counts: Record<string, number> = {
    Devanagari: 0,
    Telugu: 0,
    Tamil: 0,
    Gurmukhi: 0,
    Bengali: 0,
    Kannada: 0,
    Malayalam: 0,
    Gujarati: 0,
    Odia: 0,
    Arabic: 0,
    Latin: 0,
  };

  for (const char of text) {
    const code = char.charCodeAt(0);
    if (code >= 0x0900 && code <= 0x097F) counts.Devanagari++;
    else if (code >= 0x0C00 && code <= 0x0C7F) counts.Telugu++;
    else if (code >= 0x0B80 && code <= 0x0BFF) counts.Tamil++;
    else if (code >= 0x0A00 && code <= 0x0A7F) counts.Gurmukhi++;
    else if (code >= 0x0980 && code <= 0x09FF) counts.Bengali++;
    else if (code >= 0x0C80 && code <= 0x0CFF) counts.Kannada++;
    else if (code >= 0x0D00 && code <= 0x0D7F) counts.Malayalam++;
    else if (code >= 0x0A80 && code <= 0x0AFF) counts.Gujarati++;
    else if (code >= 0x0B00 && code <= 0x0B7F) counts.Odia++;
    else if (code >= 0x0600 && code <= 0x06FF) counts.Arabic++;
    else if ((code >= 0x0041 && code <= 0x005A) || (code >= 0x0061 && code <= 0x007A)) counts.Latin++;
  }

  let dominant = 'Latin';
  let maxCount = 0;
  for (const [script, count] of Object.entries(counts)) {
    if (count > maxCount) {
      maxCount = count;
      dominant = script;
    }
  }

  return dominant;
}

/**
 * Two-stage language detection (Script -> Language Inference -> Confidence)
 */
export function detectLanguage(text: string): DetectedLanguage {
  if (!text || text.trim().length === 0) {
    return {
      script: 'Latin',
      language: 'English',
      code: 'eng_Latn',
      confidence: 1.0,
      isRomanized: false,
      isAmbiguous: false,
    };
  }

  const script = detectScript(text);

  // 1. Script is unambiguous single-language script
  if (script === 'Telugu') {
    return { script: 'Telugu', language: 'Telugu', code: 'tel_Telu', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Tamil') {
    return { script: 'Tamil', language: 'Tamil', code: 'tam_Taml', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Gurmukhi') {
    return { script: 'Gurmukhi', language: 'Punjabi', code: 'pan_Guru', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Bengali') {
    return { script: 'Bengali', language: 'Bengali', code: 'ben_Beng', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Kannada') {
    return { script: 'Kannada', language: 'Kannada', code: 'kan_Knda', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Malayalam') {
    return { script: 'Malayalam', language: 'Malayalam', code: 'mal_Mlym', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Gujarati') {
    return { script: 'Gujarati', language: 'Gujarati', code: 'guj_Gujr', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Odia') {
    return { script: 'Odia', language: 'Odia', code: 'ori_Orya', confidence: 0.98, isRomanized: false, isAmbiguous: false };
  }
  if (script === 'Arabic') {
    return { script: 'Perso-Arabic', language: 'Urdu', code: 'urd_Arab', confidence: 0.95, isRomanized: false, isAmbiguous: false };
  }

  // 2. Shared script: Devanagari (Hindi vs Marathi vs Sanskrit)
  if (script === 'Devanagari') {
    const words = text.split(/\s+/).map((w) => w.replace(/[.,!?'"()]/g, ''));
    let marathiMatches = 0;
    const marathiMarkers = ['आहे', 'नाही', 'करतो', 'मला', 'तुला', 'होतो', 'होते', 'असा', 'अशी'];

    for (const w of words) {
      if (marathiMarkers.includes(w)) marathiMatches++;
    }

    if (marathiMatches >= 2) {
      return { script: 'Devanagari', language: 'Marathi', code: 'mar_Deva', confidence: 0.88, isRomanized: false, isAmbiguous: false };
    }
    return { script: 'Devanagari', language: 'Hindi', code: 'hin_Deva', confidence: 0.94, isRomanized: false, isAmbiguous: false };
  }

  // 3. Script is Latin: Heuristic inference between English and Romanized Indic languages
  const rawWords = text.toLowerCase().split(/\s+/).map((w) => w.replace(/[^a-z]/g, '')).filter(Boolean);
  if (rawWords.length === 0) {
    return { script: 'Latin', language: 'English', code: 'eng_Latn', confidence: 1.0, isRomanized: false, isAmbiguous: false };
  }

  let englishScore = 0;
  const indicScores: Record<string, number> = {
    hin_Deva: 0,
    tel_Telu: 0,
    tam_Taml: 0,
    pan_Guru: 0,
  };

  for (const word of rawWords) {
    if (ENGLISH_COMMON_WORDS.has(word)) {
      englishScore++;
    }
    for (const [langCode, lexicon] of Object.entries(ROMANIZED_INDIC_LEXICON)) {
      if (lexicon.includes(word)) {
        indicScores[langCode] += 1.5;
      }
    }
  }

  let topIndicLang = 'hin_Deva';
  let topIndicScore = 0;
  for (const [lang, score] of Object.entries(indicScores)) {
    if (score > topIndicScore) {
      topIndicScore = score;
      topIndicLang = lang;
    }
  }

  // If Indian lexicon density is significant
  if (topIndicScore >= 2 && topIndicScore > englishScore) {
    const langObj = SUPPORTED_LANGUAGES.find((l) => l.code === topIndicLang);
    return {
      script: 'Latin (Romanized)',
      language: langObj?.name || 'Hindi',
      code: topIndicLang,
      confidence: Math.min(0.85, 0.5 + topIndicScore * 0.1),
      isRomanized: true,
      isAmbiguous: true, // Marked ambiguous so user can easily override
    };
  }

  // Default to English
  const englishConfidence = rawWords.length > 0 ? Math.min(0.95, 0.6 + (englishScore / rawWords.length) * 0.4) : 0.7;
  return {
    script: 'Latin',
    language: 'English',
    code: 'eng_Latn',
    confidence: englishConfidence,
    isRomanized: false,
    isAmbiguous: englishConfidence < 0.75,
  };
}
