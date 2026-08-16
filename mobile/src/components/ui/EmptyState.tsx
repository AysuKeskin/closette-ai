import { StyleSheet, View } from 'react-native';

import { colors, radius, spacing } from '../../theme';
import { AppText } from './AppText';
import { Button } from './Button';
import { Icon, IconName } from './Icon';

type Props = {
  /** Custom icon (preferred). Falls back to `emoji`. */
  icon?: IconName;
  emoji?: string;
  title: string;
  message?: string;
  actionLabel?: string;
  onAction?: () => void;
};

export function EmptyState({ icon, emoji = '✨', title, message, actionLabel, onAction }: Props) {
  return (
    <View style={styles.wrap}>
      <View style={styles.badge}>
        {icon ? <Icon name={icon} size={44} /> : <AppText variant="h1">{emoji}</AppText>}
      </View>
      <AppText variant="title" center>
        {title}
      </AppText>
      {message ? (
        <AppText variant="body" tone="secondary" center style={styles.message}>
          {message}
        </AppText>
      ) : null}
      {actionLabel && onAction ? (
        <Button label={actionLabel} onPress={onAction} fullWidth={false} style={styles.action} />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: 'center', paddingVertical: spacing.xxxl, gap: spacing.md },
  badge: {
    width: 76,
    height: 76,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.surfaceAlt,
  },
  message: { maxWidth: 280 },
  action: { marginTop: spacing.md, paddingHorizontal: spacing.xxl },
});
