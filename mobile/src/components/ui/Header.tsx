import { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { spacing } from '../../theme';
import { AppText } from './AppText';

type Props = {
  title?: string;
  subtitle?: string;
  onBack?: () => void;
  right?: ReactNode;
};

export function Header({ title, subtitle, onBack, right }: Props) {
  return (
    <View style={styles.wrap}>
      <View style={styles.row}>
        {onBack ? (
          <Pressable onPress={onBack} accessibilityRole="button" accessibilityLabel="Go back" hitSlop={12}>
            <AppText variant="h2" tone="secondary">
              {'‹'}
            </AppText>
          </Pressable>
        ) : null}
        <View style={styles.titles}>
          {title ? <AppText variant="h1">{title}</AppText> : null}
          {subtitle ? (
            <AppText variant="body" tone="secondary" style={styles.subtitle}>
              {subtitle}
            </AppText>
          ) : null}
        </View>
        {right ? <View>{right}</View> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { paddingTop: spacing.md, paddingBottom: spacing.lg },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  titles: { flex: 1, gap: spacing.xs },
  subtitle: { marginTop: 2 },
});
