import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, StyleSheet, View } from 'react-native';

import { toApiError, toFieldErrors } from '../../api/client';
import { AppText, Button, Icon, LinkText, Screen, TextField } from '../../components/ui';
import { useLogin } from '../../features/auth';
import { useT } from '../../i18n';
import { spacing } from '../../theme';
import type { AuthStackParamList } from '../../navigation/types';

type FieldErrors = { email?: string; password?: string; form?: string };

export function LoginScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const login = useLogin();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});

  const onSubmit = () => {
    // Per-field validation first, so each message renders under its own input.
    const next: FieldErrors = {};
    if (!email.trim()) next.email = text('auth.validation.emailBlank');
    if (!password) next.password = text('auth.validation.passwordBlank');
    setErrors(next);
    if (next.email || next.password) return;

    login.mutate(
      { email: email.trim(), password },
      {
        onError: (err) => {
          const api = toApiError(err);
          const fields = toFieldErrors(api.message);
          setErrors({
            email: fields.email,
            password: fields.password,
            // Auth failures (e.g. wrong credentials) have no field prefix.
            form: fields.form ?? (!fields.email && !fields.password ? api.message : undefined),
          });
        },
      },
    );
  };

  return (
    <Screen scroll>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.brand}>
          <View style={styles.brandRow}>
            <Icon name="brand" size={64} />
            <AppText variant="display" tone="brand">
              Closette
            </AppText>
          </View>
          <AppText variant="body" tone="secondary">
            {text('auth.tagline')}
          </AppText>
        </View>

        <View style={styles.form}>
          <TextField
            label={text('auth.email')}
            value={email}
            onChangeText={(t) => {
              setEmail(t);
              if (errors.email) setErrors((e) => ({ ...e, email: undefined }));
            }}
            autoCapitalize="none"
            keyboardType="email-address"
            autoComplete="email"
            placeholder={text('auth.emailPlaceholder')}
            error={errors.email}
          />
          <TextField
            label={text('auth.password')}
            value={password}
            onChangeText={(t) => {
              setPassword(t);
              if (errors.password) setErrors((e) => ({ ...e, password: undefined }));
            }}
            secureTextEntry
            placeholder={text('auth.passwordPlaceholder')}
            error={errors.password}
          />
          {errors.form ? (
            <AppText variant="caption" tone="danger" style={styles.formError}>
              {`⚠ ${errors.form}`}
            </AppText>
          ) : null}
          <View style={styles.forgotRow}>
            <LinkText
              action={text('auth.forgotPassword')}
              onPress={() => navigation.navigate('ForgotPassword', { email: email.trim() || undefined })}
            />
          </View>
          <Button label={text('auth.logIn')} onPress={onSubmit} loading={login.isPending} />
        </View>

        <View style={styles.footer}>
          <LinkText
            lead={text('auth.newHere')}
            action={text('auth.createAnAccount')}
            onPress={() => navigation.navigate('Register')}
          />
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  brand: { marginTop: spacing.xxxl, marginBottom: spacing.xxl, gap: spacing.sm },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, marginLeft: -spacing.xs },
  form: { gap: spacing.md },
  formError: { marginLeft: spacing.xs },
  // Forgot-password sits snug under the password, right-aligned (standard pattern).
  forgotRow: { alignItems: 'flex-end', marginTop: -spacing.xs, marginBottom: spacing.xs },
  footer: { alignItems: 'center', marginTop: spacing.xl },
});
