/**
 * Normalized Phonetic Transliteration Engine (Music-Standard Romanization)
 * 
 * Uses an intermediate phonetic token representation to provide accurate,
 * bidirectional script conversion across Indian scripts & Roman Latin.
 * Implements standard music/internet Romanization ("Tu hi meri duniya hai").
 */

export type ScriptOption = 'original' | 'roman' | 'devanagari' | 'telugu' | 'punjabi' | 'tamil' | 'dual';

export interface ScriptItem {
  id: ScriptOption;
  label: string;
  native: string;
  icon?: string;
}

export const SCRIPT_OPTIONS: ScriptItem[] = [
  { id: 'original', label: 'Original Source', native: 'Original', icon: '🌐' },
  { id: 'dual', label: 'Dual Script (Subtitled)', native: 'Dual Mode', icon: '✨' },
  { id: 'roman', label: 'Roman / English (Aa)', native: 'Aa (English)', icon: '🔤' },
  { id: 'devanagari', label: 'Hindi (Devanagari)', native: 'हिन्दी', icon: '🇮🇳' },
  { id: 'telugu', label: 'Telugu', native: 'తెలుగు', icon: '🇮🇳' },
  { id: 'punjabi', label: 'Punjabi (Gurmukhi)', native: 'ਪੰਜਾਬੀ', icon: '🇮🇳' },
  { id: 'tamil', label: 'Tamil', native: 'தமிழ்', icon: '🇮🇳' },
];

// ─────────────────────────────────────────────────────────────
// PHONETIC PHONEME DICTIONARIES
// ─────────────────────────────────────────────────────────────

interface PhonemeDef {
  roman: string;
  musicRoman?: string; // Standard music streaming Romanization (e.g. 'i' instead of 'ee')
  devanagari: string;
  telugu: string;
  punjabi: string;
  tamil: string;
}

const INDEPENDENT_VOWELS: PhonemeDef[] = [
  { roman: 'a',  musicRoman: 'a',  devanagari: 'अ', telugu: 'అ', punjabi: 'ਅ', tamil: 'அ' },
  { roman: 'aa', musicRoman: 'aa', devanagari: 'आ', telugu: 'ఆ', punjabi: 'ਆ', tamil: 'ஆ' },
  { roman: 'i',  musicRoman: 'i',  devanagari: 'इ', telugu: 'ఇ', punjabi: 'ਇ', tamil: 'இ' },
  { roman: 'ee', musicRoman: 'i',  devanagari: 'ई', telugu: 'ఈ', punjabi: 'ਈ', tamil: 'ஈ' },
  { roman: 'u',  musicRoman: 'u',  devanagari: 'उ', telugu: 'ఉ', punjabi: 'ਉ', tamil: 'உ' },
  { roman: 'oo', musicRoman: 'u',  devanagari: 'ऊ', telugu: 'ఊ', punjabi: 'ਊ', tamil: 'ஊ' },
  { roman: 'e',  musicRoman: 'e',  devanagari: 'ए', telugu: 'ఎ', punjabi: 'ਏ', tamil: 'எ' },
  { roman: 'ai', musicRoman: 'ai', devanagari: 'ऐ', telugu: 'ఐ', punjabi: 'ਐ', tamil: 'ஐ' },
  { roman: 'o',  musicRoman: 'o',  devanagari: 'ओ', telugu: 'ఒ', punjabi: 'ਓ', tamil: 'ஒ' },
  { roman: 'au', musicRoman: 'au', devanagari: 'औ', telugu: 'ఔ', punjabi: 'ਔ', tamil: 'ஔ' },
];

const DEPENDENT_MATRAS: PhonemeDef[] = [
  { roman: 'aa', musicRoman: 'a',  devanagari: 'ा', telugu: 'ా', punjabi: 'ਾ', tamil: 'ா' },
  { roman: 'i',  musicRoman: 'i',  devanagari: 'ि', telugu: 'ి', punjabi: 'ਿ', tamil: 'ி' },
  { roman: 'ee', musicRoman: 'i',  devanagari: 'ी', telugu: 'ీ', punjabi: 'ੀ', tamil: 'ீ' },
  { roman: 'u',  musicRoman: 'u',  devanagari: 'ु', telugu: 'ు', punjabi: 'ੁ', tamil: 'ு' },
  { roman: 'oo', musicRoman: 'u',  devanagari: 'ू', telugu: 'ూ', punjabi: 'ੂ', tamil: 'ூ' },
  { roman: 'e',  musicRoman: 'e',  devanagari: 'े', telugu: 'ె', punjabi: 'ੇ', tamil: 'ெ' },
  { roman: 'ai', musicRoman: 'ai', devanagari: 'ै', telugu: 'ై', punjabi: 'ੈ', tamil: 'ை' },
  { roman: 'o',  musicRoman: 'o',  devanagari: 'ो', telugu: 'ొ', punjabi: 'ੋ', tamil: 'ொ' },
  { roman: 'au', musicRoman: 'au', devanagari: 'ौ', telugu: 'ౌ', punjabi: 'ੌ', tamil: 'ௌ' },
];

const CONSONANTS: PhonemeDef[] = [
  { roman: 'k',   musicRoman: 'k',   devanagari: 'क', telugu: 'క', punjabi: 'ਕ', tamil: 'க' },
  { roman: 'kh',  musicRoman: 'kh',  devanagari: 'ख', telugu: 'ఖ', punjabi: 'ਖ', tamil: 'க' },
  { roman: 'g',   musicRoman: 'g',   devanagari: 'ग', telugu: 'గ', punjabi: 'ਗ', tamil: 'க' },
  { roman: 'gh',  musicRoman: 'gh',  devanagari: 'घ', telugu: 'ఘ', punjabi: 'ਘ', tamil: 'க' },
  { roman: 'ng',  musicRoman: 'ng',  devanagari: 'ङ', telugu: 'ఙ', punjabi: 'ਙ', tamil: 'ங' },
  { roman: 'ch',  musicRoman: 'ch',  devanagari: 'च', telugu: 'చ', punjabi: 'ਚ', tamil: 'ச' },
  { roman: 'chh', musicRoman: 'chh', devanagari: 'छ', telugu: 'ఛ', punjabi: 'ਛ', tamil: 'ச' },
  { roman: 'j',   musicRoman: 'j',   devanagari: 'ज', telugu: 'జ', punjabi: 'ਜ', tamil: 'ஜ' },
  { roman: 'jh',  musicRoman: 'jh',  devanagari: 'झ', telugu: 'ఝ', punjabi: 'ਝ', tamil: 'ச' },
  { roman: 'ny',  musicRoman: 'ny',  devanagari: 'ञ', telugu: 'ఞ', punjabi: 'ਞ', tamil: 'ஞ' },
  { roman: 't',   musicRoman: 't',   devanagari: 'त', telugu: 'త', punjabi: 'ਤ', tamil: 'த' },
  { roman: 'th',  musicRoman: 'th',  devanagari: 'थ', telugu: 'థ', punjabi: 'ਥ', tamil: 'த' },
  { roman: 'd',   musicRoman: 'd',   devanagari: 'द', telugu: 'ద', punjabi: 'ਦ', tamil: 'த' },
  { roman: 'dh',  musicRoman: 'dh',  devanagari: 'ध', telugu: 'ధ', punjabi: 'ਧ', tamil: 'த' },
  { roman: 'n',   musicRoman: 'n',   devanagari: 'न', telugu: 'న', punjabi: 'ਨ', tamil: 'ந' },
  // Retroflex series (ट ठ ड ढ ण) — extremely common in Hindi
  { roman: 'T',   musicRoman: 't',   devanagari: 'ट', telugu: 'ట', punjabi: 'ਟ', tamil: 'ட' },
  { roman: 'Th',  musicRoman: 'th',  devanagari: 'ठ', telugu: 'ఠ', punjabi: 'ਠ', tamil: 'ட' },
  { roman: 'D',   musicRoman: 'd',   devanagari: 'ड', telugu: 'డ', punjabi: 'ਡ', tamil: 'ட' },
  { roman: 'Dh',  musicRoman: 'dh',  devanagari: 'ढ', telugu: 'ఢ', punjabi: 'ਢ', tamil: 'ட' },
  { roman: 'N',   musicRoman: 'n',   devanagari: 'ण', telugu: 'ణ', punjabi: 'ਣ', tamil: 'ண' },
  // Nukta variants (ड़ ढ़)
  { roman: 'R',   musicRoman: 'r',   devanagari: 'ड़', telugu: 'డ', punjabi: 'ੜ', tamil: 'ர' },
  { roman: 'Rh',  musicRoman: 'rh',  devanagari: 'ढ़', telugu: 'ఢ', punjabi: 'ੜ੍ਹ', tamil: 'ர' },
  { roman: 'p',   musicRoman: 'p',   devanagari: 'प', telugu: 'ప', punjabi: 'ਪ', tamil: 'ப' },
  { roman: 'ph',  musicRoman: 'ph',  devanagari: 'फ', telugu: 'ఫ', punjabi: 'ਫ', tamil: 'ப' },
  { roman: 'b',   musicRoman: 'b',   devanagari: 'ब', telugu: 'బ', punjabi: 'ਬ', tamil: 'ப' },
  { roman: 'bh',  musicRoman: 'bh',  devanagari: 'भ', telugu: 'భ', punjabi: 'ਭ', tamil: 'ப' },
  { roman: 'm',   musicRoman: 'm',   devanagari: 'म', telugu: 'మ', punjabi: 'ਮ', tamil: 'ம' },
  { roman: 'y',   musicRoman: 'y',   devanagari: 'य', telugu: 'య', punjabi: 'ਯ', tamil: 'ய' },
  { roman: 'r',   musicRoman: 'r',   devanagari: 'र', telugu: 'ర', punjabi: 'ਰ', tamil: 'ர' },
  { roman: 'l',   musicRoman: 'l',   devanagari: 'ल', telugu: 'ల', punjabi: 'ਲ', tamil: 'ல' },
  { roman: 'v',   musicRoman: 'v',   devanagari: 'व', telugu: 'వ', punjabi: 'ਵ', tamil: 'வ' },
  { roman: 'w',   musicRoman: 'w',   devanagari: 'व', telugu: 'వ', punjabi: 'ਵ', tamil: 'வ' },
  { roman: 'sh',  musicRoman: 'sh',  devanagari: 'श', telugu: 'శ', punjabi: 'ਸ', tamil: 'ஷ' },
  { roman: 's',   musicRoman: 's',   devanagari: 'स', telugu: 'స', punjabi: 'ਸ', tamil: 'ஸ' },
  { roman: 'h',   musicRoman: 'h',   devanagari: 'ह', telugu: 'హ', punjabi: 'ਹ', tamil: 'ஹ' },
  { roman: 'z',   musicRoman: 'z',   devanagari: 'ज़', telugu: 'జ', punjabi: 'ਜ਼', tamil: 'ஜ' },
  { roman: 'f',   musicRoman: 'f',   devanagari: 'फ़', telugu: 'ఫ', punjabi: 'ਫ਼', tamil: 'ப' },
  { roman: 'q',   musicRoman: 'q',   devanagari: 'क़', telugu: 'క', punjabi: 'ਕ', tamil: 'க' },
];

const SIGNS: Record<string, { devanagari: string; telugu: string; punjabi: string; tamil: string; roman: string }> = {
  virama: { devanagari: '्', telugu: '్', punjabi: '੍', tamil: '்', roman: '' },
  anusvara: { devanagari: 'ं', telugu: 'ం', punjabi: 'ਂ', tamil: 'ம்', roman: 'n' },
  chandrabindu: { devanagari: 'ँ', telugu: 'ఁ', punjabi: 'ਁ', tamil: '', roman: 'n' },
  visarga: { devanagari: 'ः', telugu: 'ః', punjabi: '', tamil: 'ஃ', roman: 'h' },
};

// ─────────────────────────────────────────────────────────────
// PHONETIC INTERMEDIATE REPRESENTATION
// ─────────────────────────────────────────────────────────────

interface PhonemeToken {
  type: 'consonant' | 'vowel' | 'matra' | 'sign' | 'other';
  phoneme: string;
  musicRoman: string;
  raw: string;
}

/**
 * Parses native Indic or Roman text into normalized phonetic tokens
 */
export function textToPhonemes(text: string): PhonemeToken[] {
  if (!text) return [];
  // Normalize Unicode to NFC so decomposed characters (like ड + ़) are combined into single precomposed characters (ड़)
  const normalized = text.normalize('NFC');
  const tokens: PhonemeToken[] = [];
  const len = normalized.length;

  for (let i = 0; i < len; i++) {
    const char = normalized[i];
    const nextChar = i < len - 1 ? normalized[i + 1] : '';
    const twoChar = char + nextChar;

    // Ignore stray nukta combining mark if already processed
    if (char === '\u093C') continue;

    // 1. Check Devanagari / Gurmukhi / Telugu / Tamil Consonants
    const matchedCons = CONSONANTS.find(
      (c) => c.devanagari === twoChar || c.devanagari === char ||
             c.telugu === char || c.punjabi === twoChar || c.punjabi === char || c.tamil === char ||
             (char === '\u095C' && c.devanagari === 'ड़') ||
             (char === '\u095D' && c.devanagari === 'ढ़')
    );
    if (matchedCons) {
      const isTwo = matchedCons.devanagari === twoChar || matchedCons.punjabi === twoChar;
      tokens.push({
        type: 'consonant',
        phoneme: matchedCons.roman,
        musicRoman: matchedCons.musicRoman || matchedCons.roman,
        raw: isTwo ? twoChar : char,
      });
      if (isTwo) i++;
      continue;
    }

    // 2. Check Independent Vowels
    const matchedVowel = INDEPENDENT_VOWELS.find(
      (v) => v.devanagari === char || v.telugu === char || v.punjabi === char || v.tamil === char
    );
    if (matchedVowel) {
      tokens.push({
        type: 'vowel',
        phoneme: matchedVowel.roman,
        musicRoman: matchedVowel.musicRoman || matchedVowel.roman,
        raw: char,
      });
      continue;
    }

    // 3. Check Matras (Dependent Vowels)
    const matchedMatra = DEPENDENT_MATRAS.find(
      (m) => m.devanagari === char || m.telugu === char || m.punjabi === char || m.tamil === char
    );
    if (matchedMatra) {
      tokens.push({
        type: 'matra',
        phoneme: matchedMatra.roman,
        musicRoman: matchedMatra.musicRoman || matchedMatra.roman,
        raw: char,
      });
      continue;
    }

    // 4. Check Signs (Virama / Anusvara)
    if (char === '्' || char === '్' || char === '੍' || char === '்') {
      tokens.push({ type: 'sign', phoneme: 'virama', musicRoman: '', raw: char });
      continue;
    }
    if (char === 'ं' || char === 'ం' || char === 'ਂ' || char === 'ँ') {
      tokens.push({ type: 'sign', phoneme: 'anusvara', musicRoman: 'n', raw: char });
      continue;
    }

    // 5. Check Roman Alphabet digraphs/letters
    if (/[a-zA-Z]/.test(char)) {
      const lower3 = text.substring(i, i + 3).toLowerCase();
      const lower2 = text.substring(i, i + 2).toLowerCase();
      const lower1 = char.toLowerCase();

      // Check 3/2/1 letter consonant
      const rCons = CONSONANTS.find((c) => c.roman === lower3 || c.roman === lower2 || c.roman === lower1);
      if (rCons) {
        tokens.push({
          type: 'consonant',
          phoneme: rCons.roman,
          musicRoman: rCons.musicRoman || rCons.roman,
          raw: rCons.roman,
        });
        i += rCons.roman.length - 1;
        continue;
      }

      // Check 2/1 letter vowel
      const rVowel = INDEPENDENT_VOWELS.find((v) => v.roman === lower2 || v.roman === lower1);
      if (rVowel) {
        tokens.push({
          type: 'vowel',
          phoneme: rVowel.roman,
          musicRoman: rVowel.musicRoman || rVowel.roman,
          raw: rVowel.roman,
        });
        i += rVowel.roman.length - 1;
        continue;
      }
    }

    // Passthrough
    tokens.push({ type: 'other', phoneme: char, musicRoman: char, raw: char });
  }

  return tokens;
}

/**
 * Converts phonetic tokens into a target script
 */
export function phonemesToScript(tokens: PhonemeToken[], target: 'roman' | 'devanagari' | 'telugu' | 'punjabi' | 'tamil'): string {
  if (target === 'roman') {
    let out = '';
    for (let i = 0; i < tokens.length; i++) {
      const t = tokens[i];
      const next = i < tokens.length - 1 ? tokens[i + 1] : null;

      if (t.type === 'consonant') {
        out += t.musicRoman;
        // In Indic phonetics, an unvocalized consonant without a matra/virama has an implicit 'a'
        if (next && next.type !== 'matra' && next.type !== 'vowel' && next.phoneme !== 'virama' && next.type !== 'other') {
          out += 'a';
        }
      } else if (t.type === 'vowel' || t.type === 'matra') {
        out += t.musicRoman;
      } else if (t.type === 'sign') {
        if (t.phoneme === 'anusvara') out += 'n';
      } else {
        out += t.raw;
      }
    }
    return out
      .replace(/(^\s*|\.\s+)([a-z])/g, (_, p1, p2) => p1 + p2.toUpperCase())
      .replace(/\s+/g, ' ')
      .trim();
  }

  let out = '';
  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i];
    const prev = i > 0 ? tokens[i - 1] : null;

    if (t.type === 'consonant') {
      const match = CONSONANTS.find((c) => c.roman === t.phoneme);
      if (match) {
        out += match[target];
      } else {
        out += t.raw;
      }
    } else if (t.type === 'matra' || (t.type === 'vowel' && prev && prev.type === 'consonant')) {
      const match = DEPENDENT_MATRAS.find((m) => m.roman === t.phoneme);
      if (match) {
        out += match[target];
      }
    } else if (t.type === 'vowel') {
      const match = INDEPENDENT_VOWELS.find((v) => v.roman === t.phoneme);
      if (match) {
        out += match[target];
      } else {
        out += t.raw;
      }
    } else if (t.type === 'sign') {
      if (t.phoneme === 'virama') out += SIGNS.virama[target];
      if (t.phoneme === 'anusvara') out += SIGNS.anusvara[target];
    } else {
      out += t.raw;
    }
  }

  return out;
}

/**
 * Universal Transliteration Method
 */
export function transliterateText(text: string, target: ScriptOption = 'roman'): string {
  if (!text || target === 'original' || target === 'dual') return text;

  const targetLang = target as 'roman' | 'devanagari' | 'telugu' | 'punjabi' | 'tamil';
  const tokens = textToPhonemes(text);
  return phonemesToScript(tokens, targetLang);
}

/**
 * Convenience method for Music-Standard Romanization
 */
export function transliterateToRoman(text: string): string {
  return transliterateText(text, 'roman');
}

/**
 * Check if text contains non-Latin regional script characters
 */
export function hasNonLatinCharacters(text: string): boolean {
  if (!text) return false;
  return /[\u0900-\u0D7F\uAC00-\uD7AF\u0400-\u04FF]/.test(text);
}
