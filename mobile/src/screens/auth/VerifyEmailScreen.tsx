import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { toApiError, toFieldErrors } from '../../api/client';
import { AppText, Button, Card, LinkText, Screen, TextField } from '../../components/ui';
import { useResendVerification, useVerifyEmail } from '../../features/auth';
import { useAuth } from '../../store/auth';
import { spacing } from '../../theme';
import type { AppStackParamList } from '../../navigation/types';

export function VerifyEmailScreen() {
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
      setError('Enter the 6-digit code from your email');
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
      onSuccess: () => setNotice('A fresh code is on its way to your inbox.'),
      onError: (err) => setError(toApiError(err).message),
    });
  };

  return (
    <Screen scroll>
      <View style={styles.header}>
        <AppText variant="display" tone="brand">
          Verify your email
        </AppText>
        <AppText variant="body" tone="secondary">
          We sent a 6-digit code to{' '}
          <AppText variant="body" tone="brand">
            {email ?? 'your email'}
          </AppText>
          . Enter it below to verify — or do it later.
        </AppText>
      </View>

      <View style={styles.form}>
        <TextField
          label="Verification code"
          value={code}
          onChangeText={(t) => {
            setCode(t.replace(/[^0-9]/g, '').slice(0, 6));
            if (error) setError(null);
          }}
          keyboardType="number-pad"
          placeholder="123456"
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

        <Button label="Verify email" onPress={onVerify} loading={verify.isPending} />

        <View style={styles.resendRow}>
          <LinkText
            lead="Didn't get it? "
            action={resend.isPending ? 'Sending…' : 'Resend code'}
            onPress={onResend}
          />
        </View>
      </View>

      <View style={styles.footer}>
        <LinkText action="I'll do this later" onPress={dismiss} />
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
