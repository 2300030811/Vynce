import { detectScript, detectLanguage } from './src/utils/languageDetection';
import { textToPhonemes, transliterateToRoman } from './src/utils/transliteration';
import { extractTextLines, reattachTimestamps, calculateContentHash } from './src/utils/lyricsTranslation';
import { runLyricsTestSuite } from './src/utils/__tests__/lyrics.test';

console.log('==================================================');
console.log(' VYNCE MULTI-LANGUAGE LYRICS TEST SUITE & BENCHMARK');
console.log('==================================================\n');

const suite = runLyricsTestSuite();
console.log(`Passed: ${suite.passed} / Total: ${suite.passed + suite.failed}`);
suite.results.forEach(r => console.log(r));

console.log('\n--- Real Song Romanization Test ---');
console.log('कोरी कमरिया यू लटक मटक जाए  ===> ', transliterateToRoman('कोरी कमरिया यू लटक मटक जाए'));
console.log('बिल्लौरी चुनरिया यू उड़ी उड़ी जाए रे ===> ', transliterateToRoman('बिल्लौरी चुनरिया यू उड़ी उड़ी जाए रे'));

console.log('\n==================================================');
if (suite.failed > 0) {
  console.error(` TEST RUN FAILED: ${suite.failed} test(s) failed`);
  console.log('==================================================');
  process.exit(1);
} else {
  console.log(' ALL LINGUISTIC & ARCHITECTURAL TESTS PASSED');
  console.log('==================================================');
}
