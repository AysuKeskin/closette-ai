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
    if (!handle) next.username = 'Username must not be blank';
    else if (!USERNAME_RE.test(handle))
      next.username = 'Username must be 3–30 characters: letters, numbers, . or _';
    if (!email.trim()) next.email = 'Email must not be blank';
    if (!password) next.password = 'Password must not be blank';
    else if (!isPasswordStrong(password)) next.password = 'Password does not meet the rules below';
    if (!confirmPassword) next.confirm = 'Please re-enter your password';
    else if (password !== confirmPassword) next.confirm = 'Passwords do not match';
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
      <Header title="Create account" subtitle="Takes less than a minute" onBack={() => navigation.goBack()} />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.form}>
          <TextField
            label="Username"
            value={username}
            onChangeText={(t) => {
              setUsername(t);
              clearError('username');
            }}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="your_username"
            hint="Only use: Letters, numbers, . and _"
            error={errors.username}
          />
          <TextField
            label="Email"
            value={email}
            onChangeText={(t) => {
              setEmail(t);
              clearError('email');
            }}
            autoCapitalize="none"
            keyboardType="email-address"
            placeholder="you@example.com"
            error={errors.email}
          />
          <View>
            <TextField
              label="Password"
              value={password}
              onChangeText={(t) => {
                setPassword(t);
                clearError('password');
              }}
              onFocus={() => setPasswordFocused(true)}
              onBlur={() => setPasswordFocused(false)}
              secureTextEntry
              placeholder="Create a strong password"
              error={errors.password}
            />
            {passwordFocused ? <PasswordChecklist password={password} /> : null}
          </View>
          <TextField
            label="Confirm password"
            value={confirmPassword}
            onChangeText={(t) => {
              setConfirmPassword(t);
              clearError('confirm');
            }}
            secureTextEntry
            placeholder="Re-enter your password"
            error={errors.confirm}
          />
          {errors.form ? (
            <AppText variant="caption" tone="danger">
              {`⚠ ${errors.form}`}
            </AppText>
          ) : null}
          <Button
            label="Create account"
            onPress={onSubmit}
            loading={register.isPending}
            style={styles.cta}
          />
        </View>

        <View style={styles.footer}>
          <LinkText
            lead="Already have an account? "
            action="Log in"
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
