import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { useT } from '../../i18n';
import { colors, spacing } from '../../theme';
import { AppText } from './AppText';

type Props = {
  message?: string;
};

export function LoadingState({ message }: Props) {
  const { t } = useT();
  return (
    <View style={styles.wrap}>
      <ActivityIndicator color={colors.primary} />
      <AppText variant="body" tone="secondary">
        {message ?? t('common.loading')}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { alignItems: 'center', justifyContent: 'center', paddingVertical: spacing.xxxl, gap: spacing.md },
});
