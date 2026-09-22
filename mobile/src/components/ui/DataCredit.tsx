import { StyleSheet } from 'react-native';

import { openLegalPage } from '../../api/consent';
import { useT } from '../../i18n';
import { spacing } from '../../theme';
import { AppText } from './AppText';

const SOURCE_URL = 'https://world.openbeautyfacts.org';

/**
 * Attribution for the product catalogue, shown wherever that catalogue is on
 * screen.
 *
 * Two licences apply and they are not the same one: the data is ODbL, the
 * photographs are CC BY-SA. Both ask to be credited where the work is used, so
 * this sits with the content rather than on an About page nobody opens. Tapping
 * opens the source, which is the other half of naming it.
 */
export function DataCredit() {
  const { t } = useT();
  return (
    <AppText
      variant="caption"
      tone="muted"
      style={styles.credit}
      onPress={() => { void openLegalPage(SOURCE_URL); }}
    >
      {t('beautyForm.dataCredit')}
    </AppText>
  );
}

const styles = StyleSheet.create({
  credit: { marginTop: spacing.xl, textAlign: 'center' },
});
