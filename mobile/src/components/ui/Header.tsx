import { ReactNode } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { useT } from '../../i18n';
import { spacing } from '../../theme';
import { AppText } from './AppText';

type Props = {
  title?: string;
  subtitle?: string;
  onBack?: () => void;
  right?: ReactNode;
  // Put the back control on its own line above the title, so title + subtitle sit flush-left
  // (instead of being indented by the inline back chevron).
  stackedBack?: boolean;
};

export function Header({ title, subtitle, onBack, right, stackedBack }: Props) {
  const { t } = useT();
  const backButton = onBack ? (
    <Pressable onPress={onBack} accessibilityRole="button" accessibilityLabel={t('common.goBack')} hitSlop={12}>
      <AppText variant="h2" tone="secondary">
        {'‹'}
      </AppText>
    </Pressable>
  ) : null;

  return (
    <View style={styles.wrap}>
      {stackedBack && backButton ? <View style={styles.backAbove}>{backButton}</View> : null}
      <View style={styles.row}>
        {!stackedBack ? backButton : null}
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
  backAbove: { alignSelf: 'flex-start', marginBottom: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  titles: { flex: 1, gap: spacing.xs },
  subtitle: { marginTop: 2 },
});
