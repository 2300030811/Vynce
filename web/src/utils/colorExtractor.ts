export interface ExtractedColors {
  dominant: string;
  accent: string;
  gradient: string;
  glow: string;
}

export async function extractColorsFromImage(imageUrl: string): Promise<ExtractedColors> {
  const defaultColors: ExtractedColors = {
    dominant: '#7c3aed',
    accent: '#ec4899',
    gradient: 'radial-gradient(circle at 50% 20%, rgba(124, 58, 237, 0.45) 0%, rgba(13, 14, 21, 0.95) 75%)',
    glow: 'rgba(124, 58, 237, 0.35)',
  };

  if (!imageUrl) return defaultColors;

  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.src = imageUrl;

    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          resolve(defaultColors);
          return;
        }

        canvas.width = 32;
        canvas.height = 32;
        ctx.drawImage(img, 0, 0, 32, 32);

        const imgData = ctx.getImageData(0, 0, 32, 32).data;
        let r = 0;
        let g = 0;
        let b = 0;
        let count = 0;

        // Sample saturated and bright pixels
        for (let i = 0; i < imgData.length; i += 4) {
          const pr = imgData[i];
          const pg = imgData[i + 1];
          const pb = imgData[i + 2];
          const brightness = (pr + pg + pb) / 3;

          // Avoid completely dark or washed out white pixels
          if (brightness > 30 && brightness < 235) {
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

          // Generate complementary / shifted accent color
          const ar = Math.min(255, Math.round(r * 0.8 + 40));
          const ag = Math.min(255, Math.round(g * 0.5 + 20));
          const ab = Math.min(255, Math.round(b * 1.2 + 60));

          const dominant = `rgb(${r}, ${g}, ${b})`;
          const accent = `rgb(${ar}, ${ag}, ${ab})`;
          const gradient = `radial-gradient(circle at 50% 15%, rgba(${r}, ${g}, ${b}, 0.5) 0%, rgba(10, 11, 16, 0.98) 75%)`;
          const glow = `rgba(${r}, ${g}, ${b}, 0.4)`;

          resolve({ dominant, accent, gradient, glow });
          return;
        }
      } catch {
        // Fallback
      }
      resolve(defaultColors);
    };

    img.onerror = () => {
      resolve(defaultColors);
    };
  });
}
