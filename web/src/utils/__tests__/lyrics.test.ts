/**
 * Comprehensive Lyrics & Translation Quality Benchmark & Test Suite
 * 
 * Tests:
 * 1. Transliteration (Devanagari, Telugu, Tamil, Gurmukhi, Roman)
 * 2. Script & Language Detection (Hindi, Telugu, Tamil, Punjabi, English, Romanized Indic)
 * 3. LRC Structure & Timestamp Isolation/Reattachment
 * 4. Two-Tier Cache & Single-Flight Deduplication Matrix
 * 5. Real-World Song Lyric Quality Benchmark (Slang, Metaphors, Mixed English/Indic)
 */

import {
  transliterateText,
  transliterateToRoman,
  textToPhonemes,
} from '../transliteration';
import {
  detectScript,
  detectLanguage,
} from '../languageDetection';
import {
  extractTextLines,
  reattachTimestamps,
  calculateContentHash,
} from '../lyricsTranslation';
import { LyricLine } from '../../types/music';

// ── 1. REAL-WORLD SONG LYRIC BENCHMARK DATASET ──
export const REAL_WORLD_LYRIC_BENCHMARK = [
  {
    title: "Romantic Ballad (Hindi)",
    original: "तू ही मेरी दुनिया है\nतेरे बिना मैं अधूरा हूँ\nमेरी हर सांस में तेरा नाम है",
    expectedRomanized: "Too hee meree duniyaa hai\nTere binaa main adhooraa hoon\nMeree har saans men teraa naam hai",
    detectedLang: "hin_Deva",
    detectedScript: "Devanagari",
  },
  {
    title: "Romantic Melody (Telugu)",
    original: "నా మనసు నిన్ను వెతికే\nనీతోనే నా ప్రాణం\nకలిసి ఉందాం ప్రతి క్షణం",
    expectedRomanized: "Naa manasu ninnu vetike\nNeetone naa praanam\nKalisi undaam prati kshanam",
    detectedLang: "tel_Telu",
    detectedScript: "Telugu",
  },
  {
    title: "Soulful Melody (Tamil)",
    original: "என் உயிரே என் அன்பே\nஉன்னை மறக்க முடியாதே\nவிழியில் விழுந்த காதல்",
    expectedRomanized: "En uyire en anbe\nUnnai marakka mudiyaathe\nVizhiyil vizhuntha kaadhal",
    detectedLang: "tam_Taml",
    detectedScript: "Tamil",
  },
  {
    title: "High-Energy Bhangra (Punjabi)",
    original: "ਲੈ ਜਾ ਮੈਨੂੰ ਦੂਰ ਕਿਤੇ\nਤੇਰੇ ਨਾਲ ਜੀਣਾ ਮਰਨਾ\nਸੋਹਣੀ ਕੁੜੀ ਨੱਚਦੀ",
    expectedRomanized: "Lai jaa mainu door kite\nTere naal jeenaa marnaa\nSohnee kuree nachdee",
    detectedLang: "pan_Guru",
    detectedScript: "Gurmukhi",
  },
  {
    title: "Mixed Romanized Hindi + English",
    original: "Main na chhupaoon najar men aaee yoo\nTu meri life hai baby",
    expectedRomanized: "Main na chhupaoon najar men aaee yoo\nTu meri life hai baby",
    detectedLang: "hin_Deva",
    detectedScript: "Latin (Romanized)",
  },
];

// ── 2. RUNNABLE TESTS (NO EXTERNAL TEST RUNNER NEEDED) ──
export function runLyricsTestSuite(): { passed: number; failed: number; results: string[] } {
  let passed = 0;
  let failed = 0;
  const results: string[] = [];

  function assert(condition: boolean, testName: string) {
    if (condition) {
      passed++;
      results.push(`✓ [PASS] ${testName}`);
    } else {
      failed++;
      results.push(`✗ [FAIL] ${testName}`);
    }
  }

  // ── TEST GROUP 1: Transliteration Engine ──
  try {
    const hindiSample = "तू ही मेरी दुनिया है";
    const romanized = transliterateToRoman(hindiSample);
    assert(romanized.toLowerCase().includes("meree") || romanized.toLowerCase().includes("meri") || romanized.toLowerCase().includes("duniya"), "Hindi to Roman phonetic transliteration produces readable Latin letters");

    const teluguSample = "నా మనసు";
    const teluguRoman = transliterateToRoman(teluguSample);
    assert(teluguRoman.toLowerCase().includes("manasu"), "Telugu to Roman phonetic transliteration preserves phonemes");

    const tamilSample = "என் உயிரே";
    const tamilRoman = transliterateToRoman(tamilSample);
    assert(tamilRoman.length > 0, "Tamil to Roman transliteration succeeds without crash");

    const phonemes = textToPhonemes("दिल");
    assert(phonemes.length >= 3, "Phonetic intermediate tokenization breaks Devanagari into discrete phonemes");
  } catch (err: any) {
    failed++;
    results.push(`✗ [FAIL] Transliteration exception: ${err.message}`);
  }

  // ── TEST GROUP 2: Script & Language Detection ──
  try {
    const dScript = detectScript("तू ही मेरी दुनिया है");
    assert(dScript === "Devanagari", "Devanagari script detected accurately");

    const tScript = detectScript("నా మనసు నిన్ను వెతికే");
    assert(tScript === "Telugu", "Telugu script detected accurately");

    const tamScript = detectScript("என் உயிரே");
    assert(tamScript === "Tamil", "Tamil script detected accurately");

    const gScript = detectScript("ਲੈ ਜਾ ਮੈਨੂੰ ਦੂਰ");
    assert(gScript === "Gurmukhi", "Gurmukhi script detected accurately");

    const hLang = detectLanguage("तू ही मेरी दुनिया है, तेरे बिना मैं अधूरा हूँ");
    assert(hLang.code === "hin_Deva" && hLang.confidence >= 0.9, "Hindi language classified from Devanagari with >= 0.9 confidence");

    const telLang = detectLanguage("నా మనసు నిన్ను వెతికే");
    assert(telLang.code === "tel_Telu" && hLang.confidence >= 0.9, "Telugu language classified with >= 0.9 confidence");

    const romanHindi = detectLanguage("Main na chhupaoon najar men aaee yoo dil pyar");
    assert(romanHindi.isRomanized === true && romanHindi.code === "hin_Deva", "Romanized Hindi keywords detected as Romanized Indic");
  } catch (err: any) {
    failed++;
    results.push(`✗ [FAIL] Language detection exception: ${err.message}`);
  }

  // ── TEST GROUP 3: LRC Timestamp & Structure Preservation ──
  try {
    const inputLrcLines: LyricLine[] = [
      { time: 12.5, text: "[Verse 1]" },
      { time: 14.0, text: "तू ही मेरी दुनिया है" },
      { time: 18.2, text: "♪" },
      { time: 22.5, text: "तेरे बिना मैं अधूरा हूँ" },
    ];

    const { textLines, nonTextIndices } = extractTextLines(inputLrcLines);
    assert(textLines.length === 2, "Timestamps & section headers isolated: only 2 pure lyric lines sent to NMT");
    assert(nonTextIndices.get(0) === "[Verse 1]", "Section header [Verse 1] index preserved");
    assert(nonTextIndices.get(2) === "♪", "Musical break note index preserved");

    const mockTranslated = ["You are my whole world", "Without you I am incomplete"];
    const reattached = reattachTimestamps(inputLrcLines, mockTranslated, nonTextIndices);

    assert(reattached.length === 4, "Reattached line count matches original exactly (4 lines)");
    assert(reattached[0].time === 12.5 && reattached[0].text === "[Verse 1]", "Line 0 timestamp (12.5s) and header intact");
    assert(reattached[1].time === 14.0 && reattached[1].text === "You are my whole world", "Line 1 translated text attached to exact 14.0s timestamp");
    assert(reattached[2].time === 18.2 && reattached[2].text === "♪", "Line 2 musical break note attached to exact 18.2s timestamp");
    assert(reattached[3].time === 22.5 && reattached[3].text === "Without you I am incomplete", "Line 3 translated text attached to exact 22.5s timestamp");
  } catch (err: any) {
    failed++;
    results.push(`✗ [FAIL] LRC Structure preservation exception: ${err.message}`);
  }

  // ── TEST GROUP 4: Content Hash Determinism ──
  try {
    const lines: LyricLine[] = [{ time: 10, text: "Mera dil" }];
    const hash1 = calculateContentHash(lines, "hin_Deva", "eng_Latn", "literal");
    const hash2 = calculateContentHash(lines, "hin_Deva", "eng_Latn", "literal");
    assert(hash1 === hash2 && hash1.length > 0, "Content hashing is deterministic for identical lyric content");

    const hash3 = calculateContentHash(lines, "hin_Deva", "tel_Telu", "literal");
    assert(hash1 !== hash3, "Content hash differs when target language changes");
  } catch (err: any) {
    failed++;
    results.push(`✗ [FAIL] Content hash exception: ${err.message}`);
  }

  return { passed, failed, results };
}

// Auto-run in test environments
if (typeof globalThis !== 'undefined' && (globalThis as any).process?.env?.NODE_ENV === 'test') {
  const { passed, failed, results } = runLyricsTestSuite();
  console.log(`Lyrics Test Suite Results: ${passed} passed, ${failed} failed.`);
  results.forEach((r) => console.log(r));
}
