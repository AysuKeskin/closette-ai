const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const axios = require('axios');

function runtime() {
  const modules = new Map(), values = new Map(), clients = [], prompts = [];
  const controls = { choice: 'accept', disclosure: 'v1', aiCalls: 0, approvals: 0, logouts: 0 };
  const mockAxios = new Proxy(axios, { get(target, key) {
    if (key !== 'create') return target[key];
    return (...args) => { const client = target.create(...args); clients.push(client); return client; };
  }});
  const mocks = {
    axios: mockAxios,
    'expo-secure-store': {
      getItemAsync: async key => values.get(key) ?? null,
      setItemAsync: async (key, value) => { values.set(key, value); },
      deleteItemAsync: async key => { if (controls.failStorage) throw new Error('keychain locked'); values.delete(key); },
    },
    'expo-constants': { expoConfig: { extra: { apiBaseUrl: 'https://api.test' } } },
    'react-native': { Platform: { OS: 'ios' }, Linking: { openURL: async () => {} },
      Alert: { alert: (title, body, buttons) => { prompts.push(body); buttons[controls.choice === 'accept' ? 2 : 0].onPress(); } } },
  };
  function load(filename) {
    if (modules.has(filename)) return modules.get(filename).exports;
    const module = { exports: {} }; modules.set(filename, module);
    const source = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
    }).outputText;
    const localRequire = name => {
      if (mocks[name]) return mocks[name];
      if (name.endsWith('/i18n')) return { translate: (_lang, key) => key };
      if (name.endsWith('/store/locale')) return { currentLanguage: () => 'en' };
      if (name.startsWith('.')) return load(path.resolve(path.dirname(filename), name + '.ts'));
      return require(name);
    };
    vm.runInNewContext(source, { module, exports: module.exports, require: localRequire,
      __DEV__: true, process, console, setTimeout, clearTimeout, URL, AbortController }, { filename });
    return module.exports;
  }
  const client = load(path.resolve(__dirname, '../src/api/client.ts'));
  const auth = load(path.resolve(__dirname, '../src/store/auth.ts')).useAuth;
  const queries = load(path.resolve(__dirname, '../src/features/queryClient.ts'));
  const response = (config, data, status = 200) => ({ config, data, status, statusText: 'OK', headers: {} });
  clients[1].defaults.adapter = async config => {
    if (config.url === '/api/privacy') return response(config, {
      version: controls.disclosure, requiresConsent: true, provider: 'Test AI', processingDetails: 'Test', privacyUrl: 'https://api.test/privacy',
    });
    if (config.url === '/api/users/me/ai-consent') controls.approvals++;
    if (config.url === '/api/auth/logout') controls.logouts++;
    return response(config, {});
  };
  client.api.defaults.adapter = async config => {
    if (config.url === '/api/users/me/ai-consent') controls.approvals++;
    else controls.aiCalls++;
    return response(config, { ok: true });
  };
  const session = id => ({ accessToken: 'access-' + id, refreshToken: 'refresh-' + id,
    user: { id, email: id + '@test.io', username: id, emailVerified: true } });
  return { client, auth, queries, controls, prompts, values, clients, response, session };
}

test('switching accounts isolates cached data and late mutation writes', async () => {
  const r = runtime();
  await r.auth.getState().setSession(r.session('A'));
  const old = r.queries.queryClient;
  old.setQueryData(['wardrobe'], ['A-private-photo']);
  await r.auth.getState().signOut();
  await r.auth.getState().setSession(r.session('B'));
  old.setQueryData(['wardrobe'], ['late-A-photo']);
  assert.equal(r.queries.queryClient.getQueryData(['wardrobe']), undefined);
  assert.equal(r.auth.getState().user.id, 'B');
  assert.equal(r.controls.logouts, 1);
});

test('an in-flight response from the old account is discarded', async () => {
  const r = runtime(); await r.auth.getState().setSession(r.session('A'));
  let finish;
  r.client.api.defaults.adapter = config => new Promise(resolve => { finish = () => resolve(r.response(config, 'private A')); });
  const request = r.client.api.get('/api/users/me');
  const check = assert.rejects(request, error => axios.isCancel(error));
  await new Promise(setImmediate);
  await r.auth.getState().signOut();
  await r.auth.getState().setSession(r.session('B'));
  finish(); await check;
});

test('a late refresh cannot resurrect a logged-out account or overwrite a new login', async () => {
  const r = runtime(); await r.auth.getState().setSession(r.session('A'));
  let finish;
  r.client.api.defaults.adapter = async config => { throw new axios.AxiosError('expired', '401', config, null, r.response(config, {}, 401)); };
  const original = r.clients[1].defaults.adapter;
  r.clients[1].defaults.adapter = config => config.url === '/api/auth/refresh'
    ? new Promise(resolve => { finish = () => resolve(r.response(config, { data: r.session('A-refreshed') })); }) : original(config);
  const request = r.client.api.get('/api/users/me');
  const check = assert.rejects(request, error => axios.isCancel(error));
  await new Promise(setImmediate);
  assert.equal(typeof finish, 'function');
  await r.auth.getState().signOut();
  await r.auth.getState().setSession(r.session('B'));
  finish(); await check;
  assert.equal(r.auth.getState().accessToken, 'access-B');
  assert.equal(r.values.get('closette_access'), 'access-B');
});

test('declining AI consent sends neither approval nor the photo request', async () => {
  const r = runtime(); await r.auth.getState().setSession(r.session('A'));
  r.controls.choice = 'cancel';
  await assert.rejects(r.client.api.post('/api/wardrobe/analyze', 'photo'), error => axios.isCancel(error));
  assert.equal(r.controls.aiCalls, 0); assert.equal(r.controls.approvals, 0);
});

test('AI requests wait for permission and changed disclosures require new permission', async () => {
  const r = runtime(); await r.auth.getState().setSession(r.session('A'));
  await r.client.api.post('/api/wardrobe/analyze', 'photo');
  await r.client.api.post('/api/outfits/generate', {});
  assert.equal(r.controls.approvals, 1); assert.equal(r.prompts.length, 1);
  r.controls.disclosure = 'v2';
  await r.client.api.post('/api/outfits/generate', {});
  assert.equal(r.controls.approvals, 2); assert.equal(r.prompts.length, 2);
});

test('production config refuses missing identity and non-public API addresses', () => {
  const config = require('../app.config.js');
  const names = ['APP_ENV', 'EAS_BUILD_PROFILE', 'EXPO_PUBLIC_API_BASE_URL', 'IOS_BUNDLE_IDENTIFIER', 'EAS_PROJECT_ID'];
  const previous = Object.fromEntries(names.map(key => [key, process.env[key]]));
  try {
    process.env.APP_ENV = 'production';
    delete process.env.EXPO_PUBLIC_API_BASE_URL;
    assert.throws(config, /HTTPS/);
    process.env.IOS_BUNDLE_IDENTIFIER = 'com.closette.mobile';
    process.env.EAS_PROJECT_ID = '11111111-2222-3333-4444-555555555555';
    for (const address of ['http://api.closette.app', 'https://localhost', 'https://10.0.0.2', 'https://api.example.com/api']) {
      process.env.EXPO_PUBLIC_API_BASE_URL = address;
      assert.throws(config, /HTTPS/);
    }
    process.env.EXPO_PUBLIC_API_BASE_URL = 'https://api.closette.app';
    const built = config();
    assert.equal(built.extra.apiBaseUrl, 'https://api.closette.app');
    assert.equal(built.ios.bundleIdentifier, 'com.closette.mobile');
  } finally {
    for (const key of names) {
      if (previous[key] === undefined) delete process.env[key]; else process.env[key] = previous[key];
    }
  }
});


test('keychain deletion failure does not prevent remote logout', async () => {
  const r = runtime(); await r.auth.getState().setSession(r.session('A'));
  r.controls.failStorage = true;
  await assert.rejects(r.auth.getState().signOut(), /keychain locked/);
  assert.equal(r.controls.logouts, 1);
  assert.equal(r.auth.getState().status, 'unauthenticated');
});
