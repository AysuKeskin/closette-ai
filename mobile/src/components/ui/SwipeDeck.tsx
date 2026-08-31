import { ReactNode, useRef, useState } from 'react';
import { Animated, Dimensions, PanResponder, StyleSheet, View } from 'react-native';

import { AppText } from './AppText';
import { colors, radius, spacing, typography } from '../../theme';

const SCREEN_W = Dimensions.get('window').width;
const SWIPE_THRESHOLD = SCREEN_W * 0.25;

type Props<T> = {
  data: T[];
  renderCard: (item: T) => ReactNode;
  onSwipe: (item: T, dir: 'left' | 'right') => void;
  onEmpty?: () => void;
};

/** A lightweight Tinder-style swipe deck (Animated + PanResponder, no extra deps).
 * Right = love, Left = skip. The card rotates and fades a LOVE/NOPE stamp. */
export function SwipeDeck<T>({ data, renderCard, onSwipe, onEmpty }: Props<T>) {
  const [index, setIndex] = useState(0);
  const position = useRef(new Animated.ValueXY()).current;

  // The PanResponder is created once, so its callbacks would capture the
  // first-render values forever. Mirror everything they read into refs that
  // each render keeps current — otherwise every swipe recomputes from index 0.
  const indexRef = useRef(0);
  const dataRef = useRef(data);
  const onSwipeRef = useRef(onSwipe);
  const onEmptyRef = useRef(onEmpty);
  dataRef.current = data;
  onSwipeRef.current = onSwipe;
  onEmptyRef.current = onEmpty;

  const rotate = position.x.interpolate({
    inputRange: [-SCREEN_W, 0, SCREEN_W],
    outputRange: ['-12deg', '0deg', '12deg'],
  });
  const likeOpacity = position.x.interpolate({ inputRange: [0, SWIPE_THRESHOLD], outputRange: [0, 1] });
  const nopeOpacity = position.x.interpolate({ inputRange: [-SWIPE_THRESHOLD, 0], outputRange: [1, 0] });
  const nextScale = position.x.interpolate({
    inputRange: [-SCREEN_W, 0, SCREEN_W],
    outputRange: [1, 0.94, 1],
    extrapolate: 'clamp',
  });

  const advance = (dir: 'left' | 'right') => {
    const i = indexRef.current;
    const item = dataRef.current[i];
    position.setValue({ x: 0, y: 0 });
    const next = i + 1;
    indexRef.current = next;
    setIndex(next);
    if (item) onSwipeRef.current(item, dir);
    if (next >= dataRef.current.length) onEmptyRef.current?.();
  };

  const forceSwipe = (dir: 'left' | 'right') => {
    Animated.timing(position, {
      toValue: { x: dir === 'right' ? SCREEN_W * 1.3 : -SCREEN_W * 1.3, y: 0 },
      duration: 220,
      useNativeDriver: false,
    }).start(() => advance(dir));
  };

  const panResponder = useRef(
    PanResponder.create({
      // Claim the gesture only on a clearly horizontal drag, so a vertical drag
      // still scrolls the parent ScrollView instead of nudging the card.
      onMoveShouldSetPanResponder: (_e, g) => Math.abs(g.dx) > 4 && Math.abs(g.dx) > Math.abs(g.dy),
      onMoveShouldSetPanResponderCapture: (_e, g) => Math.abs(g.dx) > 4 && Math.abs(g.dx) > Math.abs(g.dy),
      // Once we own a horizontal swipe, don't hand it back to the ScrollView.
      onPanResponderTerminationRequest: () => false,
      onPanResponderMove: (_e, g) => position.setValue({ x: g.dx, y: g.dy }),
      onPanResponderRelease: (_e, g) => {
        if (g.dx > SWIPE_THRESHOLD) forceSwipe('right');
        else if (g.dx < -SWIPE_THRESHOLD) forceSwipe('left');
        else Animated.spring(position, { toValue: { x: 0, y: 0 }, useNativeDriver: false, friction: 6 }).start();
      },
    }),
  ).current;

  if (index >= data.length) {
    return <View style={styles.done} />;
  }

  return (
    <View style={styles.wrap}>
      {/* Next card behind */}
      {index + 1 < data.length ? (
        <Animated.View style={[styles.card, styles.behind, { transform: [{ scale: nextScale }] }]}>
          {renderCard(data[index + 1])}
        </Animated.View>
      ) : null}

      {/* Top card */}
      <Animated.View
        {...panResponder.panHandlers}
        style={[
          styles.card,
          { transform: [{ translateX: position.x }, { translateY: position.y }, { rotate }] },
        ]}
      >
        <Animated.View style={[styles.stamp, styles.like, { opacity: likeOpacity }]}>
          <AppText style={[styles.stampText, { color: colors.primary }]}>LOVE</AppText>
        </Animated.View>
        <Animated.View style={[styles.stamp, styles.nope, { opacity: nopeOpacity }]}>
          <AppText style={[styles.stampText, { color: colors.danger }]}>NOPE</AppText>
        </Animated.View>
        {renderCard(data[index])}
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  card: { position: 'absolute', top: 0, bottom: 0, width: '100%' },
  behind: { top: 0 },
  done: { flex: 1 },
  stamp: {
    position: 'absolute',
    top: spacing.lg,
    zIndex: 2,
    borderWidth: 3,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.xs,
    transform: [{ rotate: '-12deg' }],
  },
  like: { left: spacing.lg, borderColor: colors.primary },
  nope: { right: spacing.lg, borderColor: colors.danger },
  stampText: { fontSize: typography.size.h2, fontWeight: typography.weight.bold, letterSpacing: 2 },
});
