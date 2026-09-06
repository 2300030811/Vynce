import {
  argbFromHex,
  hexFromArgb,
  sourceColorFromImage,
  Hct,
  SchemeTonalSpot,
} from '@material/material-color-utilities';

export interface VynceThemeTokens {
  primary: string;
  onPrimary: string;
  primaryContainer: string;
  onPrimaryContainer: string;
  secondary: string;
  onSecondary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;
  tertiary: string;
  tertiaryContainer: string;
  surface: string;
  surfaceContainer: string;
  surfaceContainerHigh: string;
  surfaceContainerHighest: string;
  onSurface: string;
  onSurfaceVariant: string;
  outline: string;
  accent: string;
  glow: string;
  ambientGradient: string;
}

export const DEFAULT_VYNCE_SEED = '#9B6EEA'; // VyncePurple from Color.kt

export function generateVynceTheme(seedHex: string = DEFAULT_VYNCE_SEED): VynceThemeTokens {
  try {
    const argb = argbFromHex(seedHex);
    const hct = Hct.fromInt(argb);
    const dark = new SchemeTonalSpot(hct, true, 0.0);

    const primaryHex = hexFromArgb(dark.primary);
    const primaryContainerHex = hexFromArgb(dark.primaryContainer);
    const onPrimaryContainerHex = hexFromArgb(dark.onPrimaryContainer);
    const secondaryHex = hexFromArgb(dark.secondary);
    const secondaryContainerHex = hexFromArgb(dark.secondaryContainer);
    const onSecondaryContainerHex = hexFromArgb(dark.onSecondaryContainer);
    const tertiaryHex = hexFromArgb(dark.tertiary);
    const tertiaryContainerHex = hexFromArgb(dark.tertiaryContainer);
    const surfaceContainerHex = hexFromArgb(dark.surfaceContainer);
    const surfaceContainerHighHex = hexFromArgb(dark.surfaceContainerHigh);
    const surfaceContainerHighestHex = hexFromArgb(dark.surfaceContainerHighest);
    const onSurfaceHex = hexFromArgb(dark.onSurface);
    const onSurfaceVariantHex = hexFromArgb(dark.onSurfaceVariant);
    const outlineHex = hexFromArgb(dark.outline);

    return {
      primary: primaryHex,
      onPrimary: hexFromArgb(dark.onPrimary),
      primaryContainer: primaryContainerHex,
      onPrimaryContainer: onPrimaryContainerHex,
      secondary: secondaryHex,
      onSecondary: hexFromArgb(dark.onSecondary),
      secondaryContainer: secondaryContainerHex,
      onSecondaryContainer: onSecondaryContainerHex,
      tertiary: tertiaryHex,
      tertiaryContainer: tertiaryContainerHex,
      surface: '#000000', // Pure black like Vynce pureBlack mode
      surfaceContainer: surfaceContainerHex,
      surfaceContainerHigh: surfaceContainerHighHex,
      surfaceContainerHighest: surfaceContainerHighestHex,
      onSurface: onSurfaceHex,
      onSurfaceVariant: onSurfaceVariantHex,
      outline: outlineHex,
      accent: primaryHex,
      glow: `${primaryHex}40`,
      ambientGradient: `radial-gradient(circle at 50% 15%, ${primaryHex}35 0%, rgba(0, 0, 0, 0.95) 75%)`,
    };
  } catch {
    return {
      primary: '#9B6EEA',
      onPrimary: '#FFFFFF',
      primaryContainer: '#3A2966',
      onPrimaryContainer: '#E8DEFF',
      secondary: '#CCC2DC',
      onSecondary: '#332D41',
      secondaryContainer: '#4A4458',
      onSecondaryContainer: '#E8DEF8',
      tertiary: '#EFB8C8',
      tertiaryContainer: '#492532',
      surface: '#000000',
      surfaceContainer: '#141218',
      surfaceContainerHigh: '#1D1B20',
      surfaceContainerHighest: '#2B2930',
      onSurface: '#E8E6F0',
      onSurfaceVariant: '#CAC4D0',
      outline: '#938F99',
      accent: '#9B6EEA',
      glow: 'rgba(155, 110, 234, 0.35)',
      ambientGradient: 'radial-gradient(circle at 50% 15%, rgba(155, 110, 234, 0.35) 0%, rgba(0, 0, 0, 0.95) 75%)',
    };
  }
}

/**
 * Extracts the primary seed color from an image URL using HTML Canvas + Material 3 scoring
 */
export async function extractSeedColorFromImage(imageUrl: string): Promise<string> {
  if (!imageUrl) return DEFAULT_VYNCE_SEED;

  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = imageUrl;

    img.onload = async () => {
      try {
        // Use Material Color Utilities sourceColorFromImage
        const sourceArgb = await sourceColorFromImage(img);
        if (sourceArgb) {
          const hex = hexFromArgb(sourceArgb);
          resolve(hex);
          return;
        }
      } catch {
        // fallback to manual canvas sample
      }

      try {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(DEFAULT_VYNCE_SEED);
          return;
        }

        canvas.width = 48;
        canvas.height = 48;
        ctx.drawImage(img, 0, 0, 48, 48);

        const imgData = ctx.getImageData(0, 0, 48, 48).data;
        let r = 0, g = 0, b = 0, count = 0;

        for (let i = 0; i < imgData.length; i += 4) {
          const pr = imgData[i];
          const pg = imgData[i + 1];
          const pb = imgData[i + 2];
          const brightness = (pr + pg + pb) / 3;

          // Pick saturated, vibrant colors (filter out washed out whites and deep murky blacks)
          const max = Math.max(pr, pg, pb);
          const min = Math.min(pr, pg, pb);
          const saturation = max === 0 ? 0 : (max - min) / max;

          if (brightness > 25 && brightness < 235 && saturation > 0.15) {
            r += pr;
            g += pg;
            b += pb;
            count++;
          }
        }

        if (count > 0) {
          r = Math.round(r / count);
          g = Math.round(g / count);
          b = Math.round(b / count);
          const hex = `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
          resolve(hex);
          return;
        }
      } catch {
        // ignore
      }

      resolve(DEFAULT_VYNCE_SEED);
    };

    img.onerror = () => {
      resolve(DEFAULT_VYNCE_SEED);
    };
  });
}

/**
 * Injects dynamic Material 3 CSS variables into document.documentElement
 * so all components and views smoothly adapt to the active song
 */
export function applyVynceTheme(tokens: VynceThemeTokens) {
  const root = document.documentElement;
  root.style.setProperty('--vynce-primary', tokens.primary);
  root.style.setProperty('--vynce-on-primary', tokens.onPrimary);
  root.style.setProperty('--vynce-primary-container', tokens.primaryContainer);
  root.style.setProperty('--vynce-on-primary-container', tokens.onPrimaryContainer);
  root.style.setProperty('--vynce-secondary', tokens.secondary);
  root.style.setProperty('--vynce-on-secondary', tokens.onSecondary);
  root.style.setProperty('--vynce-secondary-container', tokens.secondaryContainer);
  root.style.setProperty('--vynce-on-secondary-container', tokens.onSecondaryContainer);
  root.style.setProperty('--vynce-tertiary', tokens.tertiary);
  root.style.setProperty('--vynce-tertiary-container', tokens.tertiaryContainer);
  root.style.setProperty('--vynce-surface', tokens.surface);
  root.style.setProperty('--vynce-surface-container', tokens.surfaceContainer);
  root.style.setProperty('--vynce-surface-container-high', tokens.surfaceContainerHigh);
  root.style.setProperty('--vynce-surface-container-highest', tokens.surfaceContainerHighest);
  root.style.setProperty('--vynce-on-surface', tokens.onSurface);
  root.style.setProperty('--vynce-on-surface-variant', tokens.onSurfaceVariant);
  root.style.setProperty('--vynce-outline', tokens.outline);
  root.style.setProperty('--vynce-accent', tokens.accent);
  root.style.setProperty('--vynce-glow', tokens.glow);
  root.style.setProperty('--vynce-gradient', tokens.ambientGradient);
}
