const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire, stripTypeScriptTypes } = require('node:module');

const root = path.resolve(__dirname, '..');
const hvigorfile = path.join(root, 'harmonyApp/hvigorfile.ts');
const base = 'cn.xiaobai.mafei';

// Execute the real hook with fake Hvigor services, without loading the SDK,
// reading local signing material, or starting a build.
function loadHook() {
  const requireFromHook = createRequire(hvigorfile);
  const source = stripTypeScriptTypes(fs.readFileSync(hvigorfile, 'utf8'))
    .replace(/^import \{([^}]+)\} from ('[^']+');$/gm, 'const {$1} = require($2);')
    .replace('export default', 'module.exports =');
  const module = { exports: {} };
  vm.runInNewContext(source, {
    module,
    require(id) {
      if (id === '@ohos/hvigor') return {};
      if (id === '@ohos/hvigor-ohos-plugin') {
        return { appTasks() {}, OhosPluginId: { OHOS_APP_PLUGIN: 'com.ohos.app' } };
      }
      return requireFromHook(id);
    },
  }, { filename: hvigorfile });
  return module.exports.plugins[0];
}

function runHook(mode, profile, currentProduct = 'default') {
  const sourceApp = { app: { bundleName: base, label: '$string:app_name' } };
  const sourceProfile = structuredClone(profile);
  let appModel = structuredClone(sourceApp);
  let profileModel = structuredClone(profile);
  let afterEvaluate;
  let signingUpdates = 0;
  const context = {
    getBuildMode: () => mode,
    getAppJsonOpt: () => structuredClone(appModel),
    setAppJsonOpt(value) { appModel = structuredClone(value); },
    getBuildProfileOpt: () => structuredClone(profileModel),
    setBuildProfileOpt(value) {
      profileModel = structuredClone(value);
      signingUpdates += 1;
    },
    getCurrentProduct: () => ({ getProductName: () => currentProduct }),
  };
  loadHook().apply({
    afterNodeEvaluate(callback) { afterEvaluate = callback; },
    getContext(pluginId) {
      assert.equal(pluginId, 'com.ohos.app');
      return context;
    },
  });
  assert.equal(appModel.app.bundleName, base, 'configuration waits for the node hook');
  assert.equal(typeof afterEvaluate, 'function');
  afterEvaluate();
  assert.deepEqual(profile, sourceProfile, 'local source signing configuration is not mutated');
  assert.equal(sourceApp.app.bundleName, base);
  assert.equal(appModel.app.label, sourceApp.app.label);
  return { app: appModel.app, profile: profileModel, signingUpdates };
}

const localProfile = {
  app: {
    products: [
      { name: 'default', signingConfig: 'default' },
      { name: 'other', signingConfig: 'other' },
    ],
    signingConfigs: [{ name: 'default' }, { name: 'development' }],
  },
  modules: [],
};

test('Debug selects the development identity and signing only for the current product', () => {
  const result = runHook('debug', localProfile);
  assert.equal(result.app.bundleName, `${base}.dev`);
  assert.equal(result.profile.app.products[0].signingConfig, 'development');
  assert.equal(result.profile.app.products[1].signingConfig, 'other');
  assert.equal(result.signingUpdates, 1);
});

test('Release preserves the configured official identity and signing', () => {
  const result = runHook('release', localProfile);
  assert.equal(result.app.bundleName, base);
  assert.deepEqual(result.profile, localProfile);
  assert.equal(result.signingUpdates, 0);
});

test('switching build modes leaves source configuration reusable without suffix accumulation', () => {
  for (const mode of ['debug', 'release', 'debug']) {
    const result = runHook(mode, localProfile);
    assert.equal(result.app.bundleName, mode === 'debug' ? `${base}.dev` : base);
    assert.equal(result.profile.app.products[0].signingConfig, mode === 'debug' ? 'development' : 'default');
  }
});

test('Debug identity can be synced before local signing has been configured', () => {
  const unsigned = { app: { products: [{ name: 'default' }] }, modules: [] };
  const result = runHook('debug', unsigned);
  assert.equal(result.app.bundleName, `${base}.dev`);
  assert.deepEqual(result.profile, unsigned);
  assert.equal(result.signingUpdates, 0);
});

test('an unknown product cannot silently select another product signing configuration', () => {
  assert.throws(() => runHook('debug', localProfile, 'missing'), /Unknown HarmonyOS product: missing/);
});
