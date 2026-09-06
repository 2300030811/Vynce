import fs from 'fs';
import path from 'path';
import pngToIco from 'png-to-ico';
import { Jimp } from 'jimp';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function generate() {
  const inputPng = path.join(__dirname, '../public/vynce_logo.png');
  const squarePng = path.join(__dirname, '../public/icon.png');
  const outputIco = path.join(__dirname, '../public/icon.ico');

  try {
    const image = await Jimp.read(inputPng);
    // Contain within 512x512 with transparent background
    image.contain({ w: 512, h: 512 });
    await image.write(squarePng);
    console.log('Saved 512x512 square icon to', squarePng);

    const buf = await pngToIco(squarePng);
    fs.writeFileSync(outputIco, buf);
    console.log('Successfully generated icon.ico at', outputIco);
  } catch (err) {
    console.error('Failed to generate icons:', err);
  }
}

generate();
