import 'package:flutter/material.dart';

import 'package:namida/class/route.dart';
import 'package:namida/class/track.dart';
import 'package:namida/core/enums.dart';
import 'package:namida/core/extensions.dart';
import 'package:namida/core/namida_converter_ext.dart';
import 'package:namida/ui/widgets/custom_widgets.dart';
import 'package:namida/ui/widgets/library/multi_artwork_container.dart';

class MoodsTracksSubPage {
  static Future<void> open(String name, List<Track> tracks) {
    return _MoodsTagsTracksPage(
      route: RouteType.SUBPAGE_moodsTracks,
      name: name,
      icon: LibraryTab.moods.toIcon(),
      queueSource: QueueSource.moods(name),
      tracks: tracks,
    ).navigate();
  }
}

class TagsTracksSubPage {
  static Future<void> open(String name, List<Track> tracks) {
    return _MoodsTagsTracksPage(
      route: RouteType.SUBPAGE_tagsTracks,
      name: name,
      icon: LibraryTab.tags.toIcon(),
      queueSource: QueueSource.tags(name),
      tracks: tracks,
    ).navigate();
  }
}

class RatingsTracksSubPage {
  static Future<void> open(String name, List<Track> tracks) {
    return _MoodsTagsTracksPage(
      route: RouteType.SUBPAGE_ratingTracks,
      name: name,
      icon: LibraryTab.rating.toIcon(),
      queueSource: QueueSource.rating(name),
      tracks: tracks,
    ).navigate();
  }
}

class _MoodsTagsTracksPage extends StatelessWidget with NamidaRouteWidget {
  @override
  final RouteType route;
  @override
  final String name;
  final IconData icon;
  final QueueSource queueSource;
  final List<Track> tracks;

  const _MoodsTagsTracksPage({
    required this.route,
    required this.name,
    required this.icon,
    required this.queueSource,
    required this.tracks,
  });

  @override
  Widget build(BuildContext context) {
    return BackgroundWrapper(
      child: NamidaTracksList(
        infoBox: (maxWidth) => SubpageInfoContainer(
          maxWidth: maxWidth,
          source: QueueSource.mostPlayed,
          title: name,
          subtitle: [
            tracks.length.displayTrackKeyword,
            tracks.totalDurationFormatted,
          ].join(' - '),
          heroTag: '',
          imageBuilder: (size) => MultiArtworkContainer(
            heroTag: '',
            size: size,
            tracks: tracks.toImageTracks(),
            fallbackIcon: icon,
          ),
          tracksFn: () => tracks,
        ),
        queueLength: tracks.length,
        queueSource: queueSource,
        queue: tracks,
      ),
    );
  }
}
