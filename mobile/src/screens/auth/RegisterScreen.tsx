import { useNavigation } from '@react-navigation/native';
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
import { useRegister } from '../../features/auth';
import { useT } from '../../i18n';
import { spacing } from '../../theme';
import type { AuthStackParamList } from '../../navigation/types';

type FieldErrors = {
  username?: string;
  email?: string;
  password?: string;
  confirm?: string;
  form?: string;
};

const USERNAME_RE = /^[a-zA-Z0-9._]{3,30}$/;

export function RegisterScreen() {
  const { t: text } = useT();
  const navigation = useNavigation<NativeStackNavigationProp<AuthStackParamList>>();
  const register = useRegister();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordFocused, setPasswordFocused] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});

  const clearError = (key: keyof FieldErrors) =>
    setErrors((e) => (e[key] ? { ...e, [key]: undefined } : e));

  const onSubmit = () => {
    const next: FieldErrors = {};
    const handle = username.trim();
    if (!handle) next.username = text('auth.validation.usernameBlank');
    else if (!USERNAME_RE.test(handle)) next.username = text('auth.validation.usernamePattern');
    if (!email.trim()) next.email = text('auth.validation.emailBlank');
    if (!password) next.password = text('auth.validation.passwordBlank');
    else if (!isPasswordStrong(password)) next.password = text('auth.validation.passwordWeak');
    if (!confirmPassword) next.confirm = text('auth.validation.confirmRequired');
    else if (password !== confirmPassword) next.confirm = text('auth.validation.passwordsDontMatch');
    setErrors(next);
    if (next.username || next.email || next.password || next.confirm) return;

    register.mutate(
      { email: email.trim(), username: handle, password },
      {
        onError: (err) => {
          const api = toApiError(err);
          const fields = toFieldErrors(api.message);
          const hasField = fields.username || fields.email || fields.password;
          setErrors({
            username: fields.username,
            email: fields.email,
            password: fields.password,
            form: fields.form ?? (!hasField ? api.message : undefined),
          });
        },
      },
    );
  };

  return (
    <Screen scroll>
      <Header
        title={text('auth.createAccount')}
        subtitle={text('auth.createAccountSubtitle')}
        onBack={() => navigation.goBack()}
      />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.form}>
          <TextField
            label={text('auth.username')}
            value={username}
            onChangeText={(t) => {
              setUsername(t);
              clearError('username');
            }}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder={text('auth.usernamePlaceholder')}
            hint={text('auth.usernameHint')}
            error={errors.username}
          />
          <TextField
            label={text('auth.email')}
            value={email}
            onChangeText={(t) => {
              setEmail(t);
              clearError('email');
            }}
            autoCapitalize="none"
            keyboardType="email-address"
            placeholder={text('auth.emailPlaceholder')}
            error={errors.email}
          />
          <View>
            <TextField
              label={text('auth.password')}
              value={password}
              onChangeText={(t) => {
                setPassword(t);
                clearError('password');
              }}
              onFocus={() => setPasswordFocused(true)}
              onBlur={() => setPasswordFocused(false)}
              secureTextEntry
              placeholder={text('auth.passwordCreatePlaceholder')}
              error={errors.password}
            />
            {passwordFocused ? <PasswordChecklist password={password} /> : null}
          </View>
          <TextField
            label={text('auth.confirmPassword')}
            value={confirmPassword}
            onChangeText={(t) => {
              setConfirmPassword(t);
              clearError('confirm');
            }}
            secureTextEntry
            placeholder={text('auth.confirmPasswordPlaceholder')}
            error={errors.confirm}
          />
          {errors.form ? (
            <AppText variant="caption" tone="danger">
              {`⚠ ${errors.form}`}
            </AppText>
          ) : null}
          <Button
            label={text('auth.createAccount')}
            onPress={onSubmit}
            loading={register.isPending}
            style={styles.cta}
          />
        </View>

        <View style={styles.footer}>
          <LinkText
            lead={text('auth.alreadyHaveAccount')}
            action={text('auth.logIn')}
            onPress={() => navigation.navigate('Login')}
          />
        </View>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacing.lg, marginTop: spacing.md },
  cta: { marginTop: spacing.md },
  footer: { alignItems: 'center', marginTop: spacing.xxl },
});
