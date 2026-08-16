import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, View } from 'react-native';

import { toApiError, toFieldErrors } from '../../api/client';
import {
  AppText,
  Button,
  Header,
  LinkText,
  PasswordChecklist,
  Screen,
  TextField,
  isPasswordStrong,
} from '../../components/ui';
import { useForgotPassword, useResetPassword } from '../../features/auth';
import { spacing } from '../../theme';
import type { AuthStackParamList } from '../../navigation/types';

type Errors = {
  email?: string;
  code?: string;
  newPassword?: string;
  confirm?: string;
  form?: string;
};

export function ForgotPasswordScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const route = useRoute<RouteProp<AuthStackParamList, 'ForgotPassword'>>();
  const forgot = useForgotPassword();
  const reset = useResetPassword();

  const [step, setStep] = useState<'request' | 'reset'>('request');
  const [email, setEmail] = useState(route.params?.email ?? '');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [pwFocused, setPwFocused] = useState(false);
  const [errors, setErrors] = useState<Errors>({});

  const clear = (k: keyof Errors) => setErrors((e) => (e[k] ? { ...e, [k]: undefined } : e));

  const sendCode = () => {
    const next: Errors = {};
    if (!email.trim()) next.email = 'Email must not be blank';
    setErrors(next);
    if (next.email) return;

    forgot.mutate(email.trim(), {
      onSuccess: () => setStep('reset'),
      onError: (err) => {
        const f = toFieldErrors(toApiError(err).message);
        setErrors({ email: f.email, form: f.form ?? (!f.email ? toApiError(err).message : undefined) });
      },
    });
  };

  const doReset = () => {
    const next: Errors = {};
    if (!/^\d{6}$/.test(code.trim())) next.code = 'Enter the 6-digit code from your email';
    if (!newPassword) next.newPassword = 'Password must not be blank';
    else if (!isPasswordStrong(newPassword)) next.newPassword = 'Password does not meet the rules below';
    if (!confirm) next.confirm = 'Please re-enter your password';
    else if (newPassword !== confirm) next.confirm = 'Passwords do not match';
    setErrors(next);
    if (next.code || next.newPassword || next.confirm) return;

    reset.mutate(
      { email: email.trim(), code: code.trim(), newPassword },
      {
        onSuccess: () => navigation.navigate('Login'),
        onError: (err) => {
          const f = toFieldErrors(toApiError(err).message);
          setErrors({
            code: f.code,
            newPassword: f.newPassword,
            form: f.form ?? (!f.code && !f.newPassword ? toApiError(err).message : undefined),
          });
        },
      },
    );
  };

  return (
    <Screen scroll>
      <Header
        title="Reset password"
        subtitle={step === 'request' ? 'We’ll email you a reset code' : `Enter the code sent to ${email}`}
        onBack={() => navigation.goBack()}
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {step === 'request' ? (
          <View style={styles.form}>
            <TextField
              label="Email"
              value={email}
              onChangeText={(t) => {
                setEmail(t);
                clear('email');
              }}
              autoCapitalize="none"
              keyboardType="email-address"
              autoComplete="email"
              placeholder="you@example.com"
              error={errors.email}
            />
            {errors.form ? (
              <AppText variant="caption" tone="danger">{`⚠ ${errors.form}`}</AppText>
            ) : null}
            <Button label="Send reset code" onPress={sendCode} loading={forgot.isPending} />
          </View>
        ) : (
          <View style={styles.form}>
            <TextField
              label="Verification code"
              value={code}
              onChangeText={(t) => {
                setCode(t.replace(/[^0-9]/g, '').slice(0, 6));
                clear('code');
              }}
              keyboardType="number-pad"
              placeholder="123456"
              maxLength={6}
              error={errors.code}
            />
            <View>
              <TextField
                label="New password"
                value={newPassword}
                onChangeText={(t) => {
                  setNewPassword(t);
                  clear('newPassword');
                }}
                onFocus={() => setPwFocused(true)}
                onBlur={() => setPwFocused(false)}
                secureTextEntry
                placeholder="Create a strong password"
                error={errors.newPassword}
              />
              {pwFocused ? <PasswordChecklist password={newPassword} /> : null}
            </View>
            <TextField
              label="Confirm password"
              value={confirm}
              onChangeText={(t) => {
                setConfirm(t);
                clear('confirm');
              }}
              secureTextEntry
              placeholder="Re-enter your password"
              error={errors.confirm}
            />
            {errors.form ? (
              <AppText variant="caption" tone="danger">{`⚠ ${errors.form}`}</AppText>
            ) : null}
            <Button label="Reset password" onPress={doReset} loading={reset.isPending} />
            <View style={styles.resend}>
              <LinkText
                lead="Didn't get a code? "
                action={forgot.isPending ? 'Sending…' : 'Send again'}
                onPress={sendCode}
              />
            </View>
          </View>
        )}

        <View style={styles.footer}>
          <LinkText action="Back to log in" onPress={() => navigation.navigate('Login')} />
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.lg, marginTop: spacing.md },
  resend: { alignItems: 'center' },
  footer: { alignItems: 'center', marginTop: spacing.xxl },
});
