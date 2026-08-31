import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { toApiError, toFieldErrors } from '../../api/client';
import { AppText, Button, Card, LinkText, Screen, TextField } from '../../components/ui';
import { useResendVerification, useVerifyEmail } from '../../features/auth';
import { useAuth } from '../../store/auth';
import { splitAround, useT } from '../../i18n';
import { spacing } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

export function VerifyEmailScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList, 'VerifyEmail'>>();
  const email = useAuth((s) => s.user?.email);
  const setPromptVerify = useAuth((s) => s.setPromptVerify);
  const verify = useVerifyEmail();
  const resend = useResendVerification();

  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const dismiss = () => {
    setPromptVerify(false);
    navigation.goBack();
  };

  const onVerify = () => {
    setError(null);
    setNotice(null);
    if (!/^\d{6}$/.test(code.trim())) {
      setError(text('auth.validation.codeSixDigits'));
      return;
    }
    verify.mutate(code.trim(), {
      onSuccess: () => navigation.goBack(),
      onError: (err) => {
        const fields = toFieldErrors(toApiError(err).message);
        setError(fields.code ?? fields.form ?? toApiError(err).message);
      },
    });
  };

  const onResend = () => {
    setError(null);
    setNotice(null);
    resend.mutate(undefined, {
      onSuccess: () => setNotice(text('auth.freshCodeSent')),
      onError: (err) => setError(toApiError(err).message),
    });
  };

  // The email is emphasised inside the sentence, and it sits in a different
  // place in each language — so the split follows the translation.
  const [before, after] = splitAround(text('auth.verifySentTo', { email: '{email}' }), 'email');

  return (
    <Screen scroll>
      <View style={styles.header}>
        <AppText variant="display" tone="brand">
          {text('auth.verifyBannerTitle')}
        </AppText>
        <AppText variant="body" tone="secondary">
          {before}
          <AppText variant="body" tone="brand">
            {email ?? text('auth.yourEmail')}
          </AppText>
          {after}
        </AppText>
      </View>

      <View style={styles.form}>
        <TextField
          label={text('auth.verificationCode')}
          value={code}
          onChangeText={(t) => {
            setCode(t.replace(/[^0-9]/g, '').slice(0, 6));
            if (error) setError(null);
          }}
          keyboardType="number-pad"
          placeholder={text('auth.codePlaceholder')}
          maxLength={6}
          error={error}
          style={styles.codeInput}
        />
        {notice ? (
          <Card style={styles.notice}>
            <AppText variant="label" tone="secondary">
              {`✅ ${notice}`}
            </AppText>
          </Card>
        ) : null}

        <Button label={text('auth.verifyEmail')} onPress={onVerify} loading={verify.isPending} />

        <View style={styles.resendRow}>
          <LinkText
            lead={text('auth.didntGetIt')}
            action={resend.isPending ? text('auth.sending') : text('auth.resendCode')}
            onPress={onResend}
          />
        </View>
      </View>

      <View style={styles.footer}>
        <LinkText action={text('auth.doThisLater')} onPress={dismiss} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { marginTop: spacing.xxl, marginBottom: spacing.xl, gap: spacing.sm },
  form: { gap: spacing.lg },
  codeInput: { fontSize: 24, letterSpacing: 8, textAlign: 'center' },
  notice: { backgroundColor: undefined },
  resendRow: { alignItems: 'center' },
  footer: { alignItems: 'center', marginTop: spacing.xxl },
});
