import React, { useEffect, useState, useRef } from 'react';
import { JioSaavnApi } from '../api/jiosaavn';
import { HomeModule, Song, Album, Playlist } from '../types/music';
import { useSettingsStore } from '../stores/settingsStore';
import { usePlayerStore } from '../stores/playerStore';
import { useLibraryStore } from '../stores/libraryStore';
import { SongCard } from '../components/cards/SongCard';
import { PlaylistCard } from '../components/cards/PlaylistCard';
import { AlbumCard } from '../components/cards/AlbumCard';
import {
  Loader2,
  Play,
  Flame,
  ChevronLeft,
  ChevronRight,
  Radio,
} from 'lucide-react';

interface HomePageProps {
  onSelectAlbum: (id: string) => void;
  onSelectPlaylist: (id: string) => void;
  onSelectArtist: (id: string) => void;
  onTabChange?: (tab: any) => void;
}

const POPULAR_ARTISTS = [
  { id: '459320', name: 'Arijit Singh', image: 'https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg' },
  { id: '468245', name: 'Diljit Dosanjh', image: 'https://c.saavncdn.com/artists/Diljit_Dosanjh_005_20231025073054_500x500.jpg' },
  { id: '455130', name: 'Shreya Ghoshal', image: 'https://c.saavncdn.com/artists/Shreya_Ghoshal_007_20241101074144_500x500.jpg' },
  { id: '456323', name: 'Pritam', image: 'https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg' },
  { id: '615155', name: 'The Weeknd', image: 'https://c.saavncdn.com/artists/The_Weeknd_002_20241003071400_500x500.jpg' },
  { id: '455663', name: 'Anirudh Ravichander', image: 'https://c.saavncdn.com/artists/Anirudh_Ravichander_003_20260121134149_500x500.jpg' },
  { id: '565990', name: 'Taylor Swift', image: 'https://c.saavncdn.com/artists/Taylor_Swift_003_20200226074119_500x500.jpg' },
  { id: '456269', name: 'A.R. Rahman', image: 'https://c.saavncdn.com/artists/AR_Rahman_002_20210120084455_500x500.jpg' },
  { id: '1918741', name: 'Billie Eilish', image: 'https://c.saavncdn.com/artists/Billie_Eilish_20190211151539_500x500.jpg' },
  { id: '456863', name: 'Badshah', image: 'https://c.saavncdn.com/artists/Badshah_006_20241118064015_500x500.jpg' },
];

function greeting(): string {
  const h = new Date().getHours();
  if (h < 5) return 'Good Night, Late Owl';
  if (h < 12) return 'Good Morning';
  if (h < 17) return 'Good Afternoon';
  if (h < 21) return 'Good Evening';
  return 'Good Night';
}

export const HomePage: React.FC<HomePageProps> = ({
  onSelectAlbum,
  onSelectPlaylist,
  onSelectArtist,
}) => {
  const { languages } = useSettingsStore();
  const { playSong, currentSong, status } = usePlayerStore();
  const { history } = useLibraryStore();

  const [modules, setModules] = useState<HomeModule[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let isCancelled = false;
    (async () => {
      setIsLoading(true);
      try {
        const data = await JioSaavnApi.getHome(languages.join(','));
        if (!isCancelled) setModules(data);
      } catch {
        if (!isCancelled) setModules([]);
      } finally {
        if (!isCancelled) setIsLoading(false);
      }
    })();
    return () => {
      isCancelled = true;
    };
  }, [languages]);

  const firstSongsModule = modules.find((m) => m.type === 'songs');
  const heroSong: Song | undefined = (firstSongsModule?.items as Song[])?.[0];
  const quickPicks: Song[] = (firstSongsModule?.items as Song[])?.slice(0, 8) || [];
  const otherModules = modules.filter((m) => m !== firstSongsModule);

  return (
    <div className="flex flex-col gap-8 pb-36 w-full animate-in fade-in duration-300">
      {/* ── Top Header & Greeting ──────────────────────────── */}
      <div className="flex flex-col gap-1 pt-1">
        <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight text-white font-['Outfit'] flex items-center gap-2">
          <span>{greeting()}</span>
          <span className="inline-block animate-bounce">👋</span>
        </h1>
        <p className="text-sm text-[#6B6A7A] font-medium">
          Stream 100M+ songs at 320kbps with synced karaoke lyrics & ambient lighting
        </p>
      </div>

      {/* ── Loading Skeleton ──────────────────────────────── */}
      {isLoading && (
        <div className="flex flex-col items-center justify-center min-h-[50vh] gap-3 py-16">
          <Loader2 style={{ color: 'var(--vynce-primary)' }} className="w-8 h-8 animate-spin" />
          <p className="text-sm font-semibold text-[#6B6A7A]">Assembling your music universe…</p>
        </div>
      )}

      {!isLoading && (
        <>
          {/* ── Featured Hero Spotlight Banner ────────────────── */}
          {heroSong && (
            <div
              style={{
                background:
                  'radial-gradient(ellipse at 70% 30%, var(--vynce-primary-container) 0%, rgba(18,16,24,0.95) 65%, #0A0A0A 100%)',
              }}
              className="relative overflow-hidden rounded-[36px] p-6 md:p-10 border border-white/10 shadow-2xl flex flex-col md:flex-row items-center justify-between gap-8 group"
            >
              {/* Dynamic ambient backdrop aura */}
              <div
                className="absolute right-0 top-0 w-96 h-96 pointer-events-none opacity-30 blur-3xl scale-125 transition-all duration-700"
                style={{
                  backgroundImage: `url(${heroSong.image})`,
                  backgroundSize: 'cover',
                  backgroundPosition: 'center',
                }}
              />

              {/* Left Content */}
              <div className="relative z-10 flex flex-col items-center md:items-start text-center md:text-left max-w-xl">
                <div className="flex items-center gap-2 mb-3">
                  <span
                    style={{
                      backgroundColor: 'var(--vynce-primary-container)',
                      color: 'var(--vynce-on-primary-container)',
                    }}
                    className="flex items-center gap-1 text-[11px] font-extrabold px-3 py-1 rounded-full uppercase tracking-wider shadow-sm"
                  >
                    <Flame className="w-3.5 h-3.5" /> Spotlight Track
                  </span>
                  <span className="text-xs text-[#6B6A7A] font-semibold">
                    • 320kbps High Fidelity
                  </span>
                </div>

                <h2 className="text-2xl sm:text-3xl md:text-4xl lg:text-5xl font-black text-white tracking-tight font-['Outfit'] line-clamp-2 drop-shadow-md">
                  {heroSong.name}
                </h2>
                <p className="text-sm md:text-base font-semibold text-[#E8E6F0]/80 mt-2 line-clamp-1">
                  {heroSong.primaryArtists}
                </p>

                <div className="flex items-center gap-3 mt-6">
                  <button
                    onClick={() => playSong(heroSong, firstSongsModule?.items as Song[], 0)}
                    style={{
                      backgroundColor: 'var(--vynce-primary-container)',
                      color: 'var(--vynce-on-primary-container)',
                    }}
                    className="flex items-center gap-2.5 px-7 py-3.5 rounded-full font-bold text-sm shadow-xl transition-transform active:scale-95 hover:scale-105"
                  >
                    <Play className="w-4 h-4 fill-current" />
                    <span>Listen Now</span>
                  </button>

                  <button
                    onClick={() => {
                      if (firstSongsModule?.items && (firstSongsModule.items as Song[])[0]) {
                        playSong((firstSongsModule.items as Song[])[0], firstSongsModule.items as Song[], 0);
                      }
                    }}
                    className="flex items-center gap-2 px-5 py-3.5 rounded-full bg-white/5 hover:bg-white/10 text-white font-bold text-sm transition-colors border border-white/10 shadow-md"
                  >
                    <Radio className="w-4 h-4 text-[var(--vynce-primary)]" />
                    <span>Play Mix</span>
                  </button>
                </div>
              </div>

              {/* Right: Realistic Spinning Vinyl + Cover Artwork Sleeve */}
              <div className="relative shrink-0 flex items-center justify-center">
                {/* Vinyl Record peeking out from behind sleeve */}
                <div
                  className="hidden sm:block absolute right-[-24px] w-48 h-48 md:w-56 md:h-56 rounded-full border border-white/10 shadow-2xl animate-spin-slow"
                  style={{
                    background:
                      'radial-gradient(circle, #1a1b24 0%, #0d0e15 70%, #050508 100%)',
                  }}
                >
                  <div className="absolute inset-4 rounded-full border border-white/[0.04]" />
                  <div className="absolute inset-8 rounded-full border border-white/[0.06]" />
                  <div className="absolute inset-12 rounded-full border border-white/[0.08]" />
                  <div className="absolute inset-0 m-auto w-16 h-16 rounded-full overflow-hidden border-2 border-white/20">
                    <img
                      src={heroSong.image}
                      alt="center label"
                      className="w-full h-full object-cover"
                    />
                  </div>
                </div>

                {/* Main Artwork Sleeve */}
                <div className="relative aspect-square w-48 sm:w-52 md:w-60 rounded-[28px] overflow-hidden shadow-2xl border border-white/15 bg-black z-10 transition-transform group-hover:scale-102">
                  <img
                    src={heroSong.image || '/favicon.svg'}
                    alt={heroSong.name}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      (e.target as HTMLImageElement).src = '/favicon.svg';
                    }}
                  />
                </div>
              </div>
            </div>
          )}

          {/* ── Jump Back In (Recent Listening History - Deduplicated) ──────── */}
          {(() => {
            const uniqueHistory: typeof history = [];
            const seen = new Set<string>();
            for (const item of history) {
              if (item.song && !seen.has(item.song.id)) {
                seen.add(item.song.id);
                uniqueHistory.push(item);
              }
            }

            if (uniqueHistory.length === 0) return null;

            return (
              <section className="flex flex-col gap-3">
                <SectionHeader
                  title="Jump Back In"
                  subtitle="Your Recent Listening History"
                />
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                  {uniqueHistory.slice(0, 6).map((item, idx) => (
                    <div
                      key={item.songId || idx}
                      onClick={() => playSong(item.song, uniqueHistory.map((h) => h.song), idx)}
                      className="group flex items-center gap-3 p-2.5 rounded-2xl bg-[var(--vynce-surface-container)] hover:bg-[var(--vynce-surface-container-high)] cursor-pointer transition-all border border-white/5 shadow-sm"
                    >
                      <img
                        src={item.song.image || '/favicon.svg'}
                        alt={item.song.name}
                        className="w-12 h-12 rounded-xl object-cover shrink-0 group-hover:scale-105 transition-transform"
                      />
                      <div className="flex flex-col min-w-0 flex-1">
                        <span className="text-xs font-bold text-white truncate group-hover:text-[var(--vynce-primary)] transition-colors">
                          {item.song.name}
                        </span>
                        <span className="text-[11px] text-[#6B6A7A] truncate">
                          {item.song.primaryArtists}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </section>
            );
          })()}

          {/* ── Quick Picks Bento Grid (High Density) ──────────── */}
          {quickPicks.length > 0 && (
            <section className="flex flex-col gap-3">
              <SectionHeader
                title="Quick Picks"
                subtitle="Curated for your current mood"
              />
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-2">
                {quickPicks.map((song, idx) => (
                  <SongCard
                    key={song.id}
                    song={song}
                    index={idx}
                    queueContext={firstSongsModule?.items as Song[]}
                    showAlbum={false}
                  />
                ))}
              </div>
            </section>
          )}

          {/* ── Popular Artists Circular Carousel ─────────────── */}
          <section className="flex flex-col gap-3">
            <SectionHeader
              title="Featured Artists"
              subtitle="Top Charting Creators"
            />
            <CarouselContainer>
              {POPULAR_ARTISTS.map((artist) => (
                <ArtistAvatarItem
                  key={artist.id}
                  artist={artist}
                  onClick={() => onSelectArtist(artist.id)}
                />
              ))}
            </CarouselContainer>
          </section>

          {/* ── Other Content Carousels (Albums / Playlists / Songs) ─ */}
          {otherModules.map((module) => {
            if (!module.items || module.items.length === 0) return null;

            return (
              <section key={module.id} className="flex flex-col gap-3">
                <SectionHeader title={module.title} subtitle={module.subtitle} />

                <CarouselContainer>
                  {module.type === 'playlists' &&
                    (module.items as Playlist[]).map((playlist) => (
                      <div key={playlist.id} className="shrink-0 w-36 sm:w-44 md:w-48">
                        <PlaylistCard
                          playlist={playlist}
                          onClick={() => onSelectPlaylist(playlist.id)}
                        />
                      </div>
                    ))}

                  {module.type === 'albums' &&
                    (module.items as Album[]).map((album) => (
                      <div key={album.id} className="shrink-0 w-36 sm:w-44 md:w-48">
                        <AlbumCard album={album} onClick={() => onSelectAlbum(album.id)} />
                      </div>
                    ))}

                  {module.type === 'songs' &&
                    (module.items as Song[]).map((song, idx) => (
                      <div
                        key={song.id}
                        onClick={() => playSong(song, module.items as Song[], idx)}
                        className="shrink-0 w-36 sm:w-44 md:w-48 cursor-pointer group"
                      >
                        <div className="relative aspect-square w-full rounded-2xl overflow-hidden bg-[var(--vynce-surface-container)] mb-2.5 shadow-md border border-white/5">
                          <img
                            src={song.image || '/favicon.svg'}
                            alt={song.name}
                            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                            loading="lazy"
                          />
                          {/* Play button overlay on hover */}
                          <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                            <div
                              style={{
                                backgroundColor: 'var(--vynce-primary-container)',
                                color: 'var(--vynce-on-primary-container)',
                              }}
                              className="w-11 h-11 rounded-full flex items-center justify-center shadow-xl transform scale-90 group-hover:scale-100 transition-transform"
                            >
                              <Play className="w-5 h-5 fill-current translate-x-0.5" />
                            </div>
                          </div>
                        </div>
                        <h4 className="text-xs sm:text-sm font-bold text-white truncate group-hover:text-[var(--vynce-primary)] transition-colors">
                          {song.name}
                        </h4>
                        <p className="text-[11px] text-[#6B6A7A] truncate mt-0.5 font-medium">
                          {song.primaryArtists}
                        </p>
                      </div>
                    ))}
                </CarouselContainer>
              </section>
            );
          })}
        </>
      )}
    </div>
  );
};

/* Section Header component with subtitle pill */
function SectionHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="flex flex-col">
      {subtitle && (
        <span className="text-[11px] font-extrabold uppercase tracking-widest text-[#6B6A7A]">
          {subtitle}
        </span>
      )}
      <h2 className="text-xl sm:text-2xl md:text-3xl font-extrabold text-white tracking-tight font-['Outfit']">
        {title}
      </h2>
    </div>
  );
}

/* Horizontal Carousel container with smooth Left/Right scroll buttons */
function CarouselContainer({ children }: { children: React.ReactNode }) {
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const handleScroll = (direction: 'left' | 'right') => {
    if (scrollRef.current) {
      const scrollAmount = direction === 'left' ? -480 : 480;
      scrollRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  return (
    <div className="relative group/carousel">
      {/* Left Scroll Button */}
      <button
        onClick={() => handleScroll('left')}
        className="absolute left-0 top-1/2 -translate-y-1/2 -translate-x-3 z-20 w-10 h-10 rounded-full bg-black/80 hover:bg-black text-white border border-white/15 flex items-center justify-center opacity-0 group-hover/carousel:opacity-100 transition-all shadow-xl backdrop-blur-md hover:scale-110"
        title="Scroll left"
      >
        <ChevronLeft className="w-5 h-5" />
      </button>

      {/* Carousel Scroll Track */}
      <div
        ref={scrollRef}
        className="flex gap-4 overflow-x-auto no-scrollbar pb-2 -mx-1 px-1 scroll-smooth"
      >
        {children}
      </div>

      {/* Right Scroll Button */}
      <button
        onClick={() => handleScroll('right')}
        className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-3 z-20 w-10 h-10 rounded-full bg-black/80 hover:bg-black text-white border border-white/15 flex items-center justify-center opacity-0 group-hover/carousel:opacity-100 transition-all shadow-xl backdrop-blur-md hover:scale-110"
        title="Scroll right"
      >
        <ChevronRight className="w-5 h-5" />
      </button>
    </div>
  );
}

/* Individual Artist Avatar with onError fallback */
function ArtistAvatarItem({
  artist,
  onClick,
}: {
  artist: { id: string; name: string; image: string };
  onClick: () => void;
}) {
  const [imgError, setImgError] = useState(false);

  return (
    <div
      onClick={onClick}
      className="shrink-0 w-28 sm:w-32 flex flex-col items-center gap-2 cursor-pointer group text-center"
    >
      <div className="relative aspect-square w-24 sm:w-28 rounded-full overflow-hidden bg-[var(--vynce-surface-container)] border-2 border-transparent group-hover:border-[var(--vynce-primary)] shadow-lg transition-all duration-300 group-hover:scale-105">
        {!imgError ? (
          <img
            src={artist.image}
            alt={artist.name}
            onError={() => setImgError(true)}
            className="w-full h-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center bg-gradient-to-br from-purple-700 to-indigo-900 text-white font-extrabold text-2xl font-['Outfit']">
            {artist.name.charAt(0)}
          </div>
        )}
      </div>
      <span className="text-xs font-bold text-white group-hover:text-[var(--vynce-primary)] transition-colors truncate max-w-full">
        {artist.name}
      </span>
      <span className="text-[10px] text-[#6B6A7A] -mt-1 font-medium">Artist</span>
    </div>
  );
}


