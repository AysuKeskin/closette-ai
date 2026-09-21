import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { toApiError, toFieldErrors } from '../../api/client';
import { AppText, Button, LinkText, Screen, TextField } from '../../components/ui';
import { useResendVerification, useVerifyEmail } from '../../features/auth';
import { useAuth } from '../../store/auth';
import { splitAround, useT } from '../../i18n';
import { spacing } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

/**
 * Seconds left until `iso`, ticking once a second, or null when nothing is
 * outstanding. Counting down to the server's instant rather than from a local
 * start is what keeps it honest for someone who reopens the screen later.
 */
function useCountdown(iso: string | null | undefined): number | null {
  const target = iso ? Date.parse(iso) : NaN;
  const secondsLeft = () => Math.max(0, Math.ceil((target - Date.now()) / 1000));
  const [left, setLeft] = useState(() => (Number.isNaN(target) ? 0 : secondsLeft()));

  useEffect(() => {
    if (Number.isNaN(target)) return;
    setLeft(secondsLeft());
    const id = setInterval(() => setLeft(secondsLeft()), 1000);
    return () => clearInterval(id);
  }, [target]);

  return Number.isNaN(target) ? null : left;
}

function formatRemaining(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

export function VerifyEmailScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<AppStackParamList, 'VerifyEmail'>>();
  const email = useAuth((s) => s.user?.email);
  const expiresAt = useAuth((s) => s.user?.verificationExpiresAt);
  const setPromptVerify = useAuth((s) => s.setPromptVerify);
  const verify = useVerifyEmail();
  const resend = useResendVerification();

  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const remaining = useCountdown(expiresAt);

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
          <AppText variant="label" tone="secondary">
            {notice}
          </AppText>
        ) : null}
        {remaining !== null ? (
          <AppText variant="label" tone="secondary">
            {remaining > 0
              ? text('auth.codeExpiresIn', { time: formatRemaining(remaining) })
              : text('auth.codeExpired')}
          </AppText>
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
  resendRow: { alignItems: 'center' },
  footer: { alignItems: 'center', marginTop: spacing.xxl },
});
