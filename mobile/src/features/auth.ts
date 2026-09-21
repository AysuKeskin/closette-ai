import { useMutation } from '@tanstack/react-query';

import { authApi, userApi } from '../api/endpoints';
import { useAuth } from '../store/auth';

export function useLogin() {
  const setSession = useAuth((s) => s.setSession);
  return useMutation({
    mutationFn: (args: { email: string; password: string }) =>
      authApi.login(args.email, args.password),
    onSuccess: (result) => setSession(result),
  });
}

export function useRegister() {
  const setSession = useAuth((s) => s.setSession);
  const setPromptVerify = useAuth((s) => s.setPromptVerify);
  return useMutation({
    mutationFn: (args: { email: string; username: string; password: string; displayName?: string }) =>
      authApi.register(args.email, args.username, args.password, args.displayName),
    onSuccess: async (result) => {
      await setSession(result);
      // Soft verification: open the code screen once, right after sign-up.
      setPromptVerify(!result.user.emailVerified);
    },
  });
}

export function useVerifyEmail() {
  const setUser = useAuth((s) => s.setUser);
  const setPromptVerify = useAuth((s) => s.setPromptVerify);
  return useMutation({
    mutationFn: (code: string) => userApi.verifyEmail(code),
    onSuccess: (user) => {
      setUser(user);
      setPromptVerify(false);
    },
  });
}

export function useResendVerification() {
  const setUser = useAuth((s) => s.setUser);
  return useMutation({
    mutationFn: () => userApi.resendVerification(),
    // The reply carries the new expiry, so storing it restarts the countdown.
    onSuccess: (user) => setUser(user),
  });
}

export function useForgotPassword() {
  return useMutation({
    mutationFn: (email: string) => authApi.forgotPassword(email),
  });
}

export function useResetPassword() {
  return useMutation({
    mutationFn: (args: { email: string; code: string; newPassword: string }) =>
      authApi.resetPassword(args.email, args.code, args.newPassword),
  });
}
