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
import { useT } from '../../i18n';
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
  const { t: text } = useT();
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
    if (!email.trim()) next.email = text('auth.validation.emailBlank');
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
    if (!/^\d{6}$/.test(code.trim())) next.code = text('auth.validation.codeSixDigits');
    if (!newPassword) next.newPassword = text('auth.validation.passwordBlank');
    else if (!isPasswordStrong(newPassword)) next.newPassword = text('auth.validation.passwordWeak');
    if (!confirm) next.confirm = text('auth.validation.confirmRequired');
    else if (newPassword !== confirm) next.confirm = text('auth.validation.passwordsDontMatch');
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
        title={text('auth.resetTitle')}
        subtitle={
          step === 'request'
            ? text('auth.resetSubtitleRequest')
            : text('auth.resetSubtitleCode', { email })
        }
        onBack={() => navigation.goBack()}
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        {step === 'request' ? (
          <View style={styles.form}>
            <TextField
              label={text('auth.email')}
              value={email}
              onChangeText={(t) => {
                setEmail(t);
                clear('email');
              }}
              autoCapitalize="none"
              keyboardType="email-address"
              autoComplete="email"
              placeholder={text('auth.emailPlaceholder')}
              error={errors.email}
            />
            {errors.form ? (
              <AppText variant="caption" tone="danger">{`⚠ ${errors.form}`}</AppText>
            ) : null}
            <Button label={text('auth.sendResetCode')} onPress={sendCode} loading={forgot.isPending} />
          </View>
        ) : (
          <View style={styles.form}>
            <TextField
              label={text('auth.verificationCode')}
              value={code}
              onChangeText={(t) => {
                setCode(t.replace(/[^0-9]/g, '').slice(0, 6));
                clear('code');
              }}
              keyboardType="number-pad"
              placeholder={text('auth.codePlaceholder')}
              maxLength={6}
              error={errors.code}
            />
            <View>
              <TextField
                label={text('auth.newPassword')}
                value={newPassword}
                onChangeText={(t) => {
                  setNewPassword(t);
                  clear('newPassword');
                }}
                onFocus={() => setPwFocused(true)}
                onBlur={() => setPwFocused(false)}
                secureTextEntry
                placeholder={text('auth.passwordCreatePlaceholder')}
                error={errors.newPassword}
              />
              {pwFocused ? <PasswordChecklist password={newPassword} /> : null}
            </View>
            <TextField
              label={text('auth.confirmPassword')}
              value={confirm}
              onChangeText={(t) => {
                setConfirm(t);
                clear('confirm');
              }}
              secureTextEntry
              placeholder={text('auth.confirmPasswordPlaceholder')}
              error={errors.confirm}
            />
            {errors.form ? (
              <AppText variant="caption" tone="danger">{`⚠ ${errors.form}`}</AppText>
            ) : null}
            <Button label={text('auth.resetPassword')} onPress={doReset} loading={reset.isPending} />
            <View style={styles.resend}>
              <LinkText
                lead={text('auth.didntGetCode')}
                action={forgot.isPending ? text('auth.sending') : text('auth.sendAgain')}
                onPress={sendCode}
              />
            </View>
          </View>
        )}

        <View style={styles.footer}>
          <LinkText action={text('auth.backToLogin')} onPress={() => navigation.navigate('Login')} />
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
