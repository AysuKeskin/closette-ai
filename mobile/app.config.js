const base = require('./app.json').expo;

function publicHttps(name, value) {
  let url;
  try { url = new URL(value); } catch { throw new Error(`${name} must be a public HTTPS URL`); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/'
      || ['localhost', '127.0.0.1', '10.0.2.2', '[::1]'].includes(url.hostname)
      || url.hostname.endsWith('.example') || url.hostname.endsWith('.invalid')
      || url.hostname === 'example.com' || url.hostname.endsWith('.test')
      || /^(10\.|127\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)/.test(url.hostname)) throw new Error(`${name} must be a public HTTPS URL`);
  return value.replace(/\/$/, '');
}

module.exports = () => {
  const production = process.env.APP_ENV === 'production' || process.env.EAS_BUILD_PROFILE === 'production';
  const api = process.env.EXPO_PUBLIC_API_BASE_URL;
  const bundleIdentifier = process.env.IOS_BUNDLE_IDENTIFIER;
  const projectId = process.env.EAS_PROJECT_ID;
  if (production) {
    publicHttps('EXPO_PUBLIC_API_BASE_URL', api);
    if (!bundleIdentifier || !/^[A-Za-z0-9-]+(\.[A-Za-z0-9-]+){2,}$/.test(bundleIdentifier)
        || bundleIdentifier.startsWith('com.example.')) throw new Error('Set your IOS_BUNDLE_IDENTIFIER');
    if (!projectId || !/^[0-9a-f-]{36}$/i.test(projectId)) throw new Error('Set your EAS_PROJECT_ID');
  }
  return {
    ...base,
    ios: { ...base.ios, ...(bundleIdentifier ? { bundleIdentifier } : {}) },
    extra: {
      ...base.extra,
      apiBaseUrl: api || undefined,
      ...(projectId ? { eas: { projectId } } : {}),
    },
  };
};
