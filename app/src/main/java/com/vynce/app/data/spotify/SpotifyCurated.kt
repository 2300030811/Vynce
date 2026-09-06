package com.vynce.app.data.spotify

data class SpotifyCuratedCategory(
    val id: String,
    val label: String,
    val description: String,
    val badge: String? = null
)

data class SpotifyCuratedPlaylist(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: String,
    val coverArtUrl: String,
    val gradientColors: List<Long>,
)

object SpotifyCurated {
    val CATEGORIES = listOf(
        SpotifyCuratedCategory("all", "All Mixes", "Curated music collections across all genres and moods"),
        SpotifyCuratedCategory("charts", "Top Charts", "Global and regional top charts updated daily", "Hot"),
        SpotifyCuratedCategory("desi", "Desi & Bollywood", "Bollywood, Punjabi, Telugu, and Indian indie anthems", "Trending"),
        SpotifyCuratedCategory("mood", "Mood & Vibes", "Feel-good, chill, and acoustic mood boosters"),
        SpotifyCuratedCategory("focus", "Focus & Flow", "Lo-fi, ambient, and peaceful piano for deep concentration"),
        SpotifyCuratedCategory("workout", "Energy & Workout", "High-BPM beats and motivational adrenaline"),
        SpotifyCuratedCategory("party", "Dance & Club", "Festival bangers, peak-hour electronic, and club mixes"),
        SpotifyCuratedCategory("pop", "Pop Anthems", "Trending pop hits and chart-topping viral melodies"),
        SpotifyCuratedCategory("hiphop", "Hip-Hop & Rap", "Heavy basslines, classic 90s, and modern drill"),
        SpotifyCuratedCategory("indie", "Indie & Alt", "Fresh acoustic, bedroom pop, and alternative discoveries"),
        SpotifyCuratedCategory("romance", "Acoustic & Romance", "Soulful melodies, coffeehouse acoustics, and love songs"),
        SpotifyCuratedCategory("sleep", "Ambient & Sleep", "Gentle soundscapes, ambient frequencies, and peaceful rest")
    )

    val PLAYLISTS = listOf(
        // ── CHARTS ──────────────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DXcBWIGoYBM5M",
            name = "Today's Top Hits",
            subtitle = "The hottest tracks right now worldwide",
            category = "charts",
            coverArtUrl = "",
            gradientColors = listOf(0xFF064E3B, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZEVXbMDoHDwVN2tF",
            name = "Top 50 - Global",
            subtitle = "Daily update of the most played tracks worldwide",
            category = "charts",
            coverArtUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_global_default.jpg",
            gradientColors = listOf(0xFF1E3A8A, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZEVXbLZ52XmnySJg",
            name = "Top 50 - India",
            subtitle = "Daily update of the most played tracks in India",
            category = "charts",
            coverArtUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_in_default.jpg",
            gradientColors = listOf(0xFF78350F, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZEVXbLRQDuF5jeBp",
            name = "Top 50 - USA",
            subtitle = "Daily update of the most played tracks in the United States",
            category = "charts",
            coverArtUrl = "https://charts-images.scdn.co/assets/locale_en/regional/daily/region_us_default.jpg",
            gradientColors = listOf(0xFF164E63, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX4JAvHpjipBk",
            name = "New Music Friday",
            subtitle = "The best new releases of the week",
            category = "charts",
            coverArtUrl = "",
            gradientColors = listOf(0xFF581C87, 0xFF0A0A0A)
        ),

        // ── DESI & BOLLYWOOD ────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DXdpQPPZq3F7n",
            name = "Bollywood Mush",
            subtitle = "Soulful Hindi melodies and heartwarming love anthems",
            category = "desi",
            coverArtUrl = "",
            gradientColors = listOf(0xFF881337, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX5q67ZpWyRrZ",
            name = "Indie India",
            subtitle = "The finest independent artists and sounds across India",
            category = "desi",
            coverArtUrl = "",
            gradientColors = listOf(0xFF134E4A, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX1lVhptIYRda",
            name = "Hot Country & Acoustic",
            subtitle = "Heartland acoustic anthems and contemporary sounds",
            category = "desi",
            coverArtUrl = "",
            gradientColors = listOf(0xFF7C2D12, 0xFF0A0A0A)
        ),

        // ── MOOD & VIBES ────────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX4WYpdgoIcn6",
            name = "Chill Hits",
            subtitle = "Kick back to the best chill tunes and calm melodies",
            category = "mood",
            coverArtUrl = "",
            gradientColors = listOf(0xFF115E59, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX3rxVfibe1L0",
            name = "Mood Booster",
            subtitle = "Get happy with today's dose of feel-good anthems",
            category = "mood",
            coverArtUrl = "",
            gradientColors = listOf(0xFF713F12, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX4sWSpwq3LiO",
            name = "Peaceful Piano",
            subtitle = "Relax and unwind with beautiful solo piano pieces",
            category = "mood",
            coverArtUrl = "",
            gradientColors = listOf(0xFF1E293B, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX7qK8ma5wgG1",
            name = "Sad Songs",
            subtitle = "Emotional acoustic and melancholic reflection",
            category = "mood",
            coverArtUrl = "",
            gradientColors = listOf(0xFF0C4A6E, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX3YSRoSdA634",
            name = "Life Sucks",
            subtitle = "When everything is too much, this is for you",
            category = "mood",
            coverArtUrl = "",
            gradientColors = listOf(0xFF27272A, 0xFF0A0A0A)
        ),

        // ── FOCUS & FLOW ────────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DWZeKCadgRdKQ",
            name = "Deep Focus",
            subtitle = "Ambient electronic study beats for high productivity",
            category = "focus",
            coverArtUrl = "",
            gradientColors = listOf(0xFF312E81, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DWWQRwui0ExPn",
            name = "Lo-Fi Beats",
            subtitle = "Warm analog beats to relax, code, and study to",
            category = "focus",
            coverArtUrl = "",
            gradientColors = listOf(0xFF581C87, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX8Uebhn9wzrS",
            name = "Chill Lo-Fi Study",
            subtitle = "Soft instrumental lo-fi background beats",
            category = "focus",
            coverArtUrl = "",
            gradientColors = listOf(0xFF064E3B, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DXdLEN7aqioXM",
            name = "Retrowave Flow",
            subtitle = "Retro synthwave and dreamwave drive",
            category = "focus",
            coverArtUrl = "",
            gradientColors = listOf(0xFF701A75, 0xFF0A0A0A)
        ),

        // ── ENERGY & WORKOUT ────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX76Wlfdnj7AP",
            name = "Beast Mode",
            subtitle = "Pure adrenaline and heavy bass for max performance",
            category = "workout",
            coverArtUrl = "",
            gradientColors = listOf(0xFF7F1D1D, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX70RN3TfWWJh",
            name = "Workout Motivation",
            subtitle = "Upbeat pop, hip-hop, and EDM fitness fuel",
            category = "workout",
            coverArtUrl = "",
            gradientColors = listOf(0xFF7C2D12, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX4eRPd9frC1m",
            name = "Pure Hype",
            subtitle = "Unstoppable high-energy bangers",
            category = "workout",
            coverArtUrl = "",
            gradientColors = listOf(0xFF78350F, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX32NsLKyzScr",
            name = "Power Hour",
            subtitle = "Fast-paced rhythmic motivation",
            category = "workout",
            coverArtUrl = "",
            gradientColors = listOf(0xFF881337, 0xFF0A0A0A)
        ),

        // ── DANCE & CLUB ────────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX0BcQWzuB7ZO",
            name = "Dance Hits",
            subtitle = "Club anthems and peak-hour electronic beats",
            category = "party",
            coverArtUrl = "",
            gradientColors = listOf(0xFF4C1D95, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX6J5NfMJS675",
            name = "Techno Bunker",
            subtitle = "Deep underground techno and dark industrial grooves",
            category = "party",
            coverArtUrl = "",
            gradientColors = listOf(0xFF27272A, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DXaXB8fQg7xif",
            name = "Dance Party",
            subtitle = "Festival anthems and high-energy club tracks",
            category = "party",
            coverArtUrl = "",
            gradientColors = listOf(0xFF831843, 0xFF0A0A0A)
        ),

        // ── POP ANTHEMS ─────────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DWUa8ZRTfalHk",
            name = "Pop Rising",
            subtitle = "The freshest pop anthems and viral hits",
            category = "pop",
            coverArtUrl = "",
            gradientColors = listOf(0xFF881337, 0xFF0A0A0A)
        ),

        // ── HIP-HOP & RAP ───────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX0XUsuxWHRQd",
            name = "RapCaviar",
            subtitle = "Heavy-hitting hip-hop and trap chartbusters",
            category = "hiphop",
            coverArtUrl = "",
            gradientColors = listOf(0xFF18181B, 0xFF09090B)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DWY4xHQp97fN6",
            name = "Get Turnt",
            subtitle = "High energy trap, drill, and heavy bars",
            category = "hiphop",
            coverArtUrl = "",
            gradientColors = listOf(0xFF1C1917, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX186v583rmzp",
            name = "90s Hip-Hop Anthems",
            subtitle = "Golden era hip-hop classics and boom bap beats",
            category = "hiphop",
            coverArtUrl = "",
            gradientColors = listOf(0xFF713F12, 0xFF0A0A0A)
        ),

        // ── INDIE & ALT ─────────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX2Nc3B70tvx0",
            name = "Indie's Top 50",
            subtitle = "The definitive sound of modern indie and alternative",
            category = "indie",
            coverArtUrl = "",
            gradientColors = listOf(0xFF1E3A8A, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DXdbXrPNafg9d",
            name = "All New Indie",
            subtitle = "Essential new indie rock, bedroom pop, and post-punk",
            category = "indie",
            coverArtUrl = "",
            gradientColors = listOf(0xFF134E4A, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DWWEcRhUVtL8n",
            name = "Indie Pop",
            subtitle = "Bright hooks, jangly guitars, and catchy melodies",
            category = "indie",
            coverArtUrl = "",
            gradientColors = listOf(0xFF581C87, 0xFF0A0A0A)
        ),

        // ── ACOUSTIC & ROMANCE ──────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX4E3UdUs7fUx",
            name = "Afternoon Acoustic",
            subtitle = "Warm acoustic guitars, gentle vocals, and mellow vibes",
            category = "romance",
            coverArtUrl = "",
            gradientColors = listOf(0xFF78350F, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX6ziVCJnEm59",
            name = "Your Favorite Coffeehouse",
            subtitle = "Catch up or slow down with acoustic singer-songwriters",
            category = "romance",
            coverArtUrl = "",
            gradientColors = listOf(0xFF1C1917, 0xFF0A0A0A)
        ),

        // ── AMBIENT & SLEEP ─────────────────────────────────────────────────
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DXbcPC6Vvqudd",
            name = "Night Rain",
            subtitle = "The soothing, restful sound of steady rainfall",
            category = "sleep",
            coverArtUrl = "",
            gradientColors = listOf(0xFF0369A1, 0xFF0A0A0A)
        ),
        SpotifyCuratedPlaylist(
            id = "37i9dQZF1DX3Ogo9pFvBkY",
            name = "Ambient Relaxation",
            subtitle = "Gentle soundscapes and calm textures for relaxation",
            category = "sleep",
            coverArtUrl = "",
            gradientColors = listOf(0xFF334155, 0xFF0A0A0A)
        )
    )
}
