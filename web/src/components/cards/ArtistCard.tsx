import React from 'react';
import { Artist } from '../../types/music';
import { User } from 'lucide-react';
import { formatNumber } from '../../utils/formatters';

interface ArtistCardProps {
  artist: Artist;
  onClick?: () => void;
}

export const ArtistCard: React.FC<ArtistCardProps> = ({ artist, onClick }) => {
  const [imgError, setImgError] = React.useState(false);
  const showFallback = !artist.image || imgError;
  const initial = (artist.name || 'A').trim().charAt(0).toUpperCase();

  return (
    <div
      onClick={onClick}
      className="group relative flex flex-col items-center p-3 rounded-2xl bg-[var(--vynce-surface-container)] hover:bg-[var(--vynce-surface-container-high)] cursor-pointer transition-all duration-200 text-center border border-white/5"
    >
      <div className="relative aspect-square w-full max-w-[130px] rounded-full overflow-hidden shadow-lg bg-[#181722] mb-3 border border-white/10 group-hover:border-[var(--vynce-primary)]/50 transition-colors flex items-center justify-center">
        {!showFallback ? (
          <img
            src={artist.image}
            alt={artist.name}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
            loading="lazy"
            onError={() => setImgError(true)}
          />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center bg-gradient-to-br from-purple-900/50 via-indigo-950/40 to-black/60 text-white font-extrabold select-none">
            <span className="text-2xl sm:text-3xl font-['Outfit'] text-[var(--vynce-primary)] group-hover:scale-110 transition-transform">
              {initial}
            </span>
          </div>
        )}
      </div>

      <h3 className="text-sm font-semibold text-white truncate max-w-full tracking-tight group-hover:text-[var(--vynce-primary)] transition-colors">
        {artist.name}
      </h3>
      <p className="text-xs text-[#6B6A7A] truncate mt-0.5">
        {artist.followerCount && Number(artist.followerCount) > 0
          ? `${formatNumber(artist.followerCount)} Followers`
          : 'Artist'}
      </p>
    </div>
  );
};
