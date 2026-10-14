import React from 'react';
import faviconSvg from '../assets/favicon.svg';
import vynceLogoPng from '../assets/vynce_logo.png';
import iconPng from '../assets/icon.png';

export const APP_ICONS = {
  favicon: faviconSvg,
  logo: vynceLogoPng,
  icon: iconPng,
};

export const DEFAULT_ARTWORK = faviconSvg;

/**
 * ponytail: Safe image fallback that prevents infinite reload loops under file:// or offline mode
 */
export function handleImageFallback(
  e: React.SyntheticEvent<HTMLImageElement, Event>,
  fallback: string = DEFAULT_ARTWORK
) {
  const target = e.currentTarget;
  if (!target.dataset.fallbackApplied) {
    target.dataset.fallbackApplied = 'true';
    target.src = fallback;
  }
}
