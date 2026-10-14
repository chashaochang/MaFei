import test from 'node:test';
import assert from 'node:assert/strict';
import { appBoundaryViolations as check } from './verify-app-boundaries.mjs';
const ui = 'composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/';
const home = 'feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/';

test('root permits assembly but rejects a new state owner or page implementation', () => {
  assert.deepEqual(check(`${ui}App.kt`, 'fun App() { BrowseApplication(runtime) }'), []);
  assert.ok(check(`${ui}App.kt`, 'class SearchState {}').length);
  assert.ok(check(`${ui}App.kt`, 'fun App() { runtime.sessions.media.item(id) }').length);
  assert.ok(check(`${ui}App.kt`, 'fun App() { scope.launch { load() } }').length);
});
test('comments and literal examples do not create false dependencies', () => {
  assert.deepEqual(check(`${ui}App.kt`, '// class Example\nfun App() { val text = "MediaBrowser class" }'), []);
  assert.deepEqual(check(`${ui}App.kt`, '/* runtime.connect() */\nfun App() {}'), []);
});
test('route mapping cannot acquire runtime state or repository access', () => {
  assert.deepEqual(check(`${ui}navigation/BrowseShellMapping.kt`, 'fun route() = ShellContext(HomeDestination)'), []);
  assert.ok(check(`${ui}navigation/BrowseShellMapping.kt`, 'val state = remember { MediaBrowser() }').length);
  assert.ok(check(`${ui}navigation/BrowseRoutes.kt`, 'val source: MediaRepository').length);
});
test('entries receive narrow dependencies rather than a service locator', () => {
  assert.ok(check(`${ui}browse/entries/SearchEntryBinding.kt`, 'fun Route(runtime: ApplicationRuntime) {}').length);
  assert.ok(check(`${ui}account/WebITongEntryBinding.kt`, 'val runtime = LocalApplicationRuntime.current').length);
  assert.deepEqual(check(`${ui}settings/SettingsEntryBinding.kt`, 'fun Route(store: AppearanceStore, onBack: () -> Unit) {}'), []);
});
test('facade cannot grow a second query implementation', () => {
  assert.deepEqual(check(`${home}MediaBrowser.kt`, 'fun search(term: String) = search.search(term)'), []);
  assert.ok(check(`${home}MediaBrowser.kt`, 'val pages = MediaPageAccumulator()').length);
  assert.ok(check(`${home}MediaBrowser.kt`, 'fun query() = repository.browse(session)').length);
});
test('controllers cannot own duplicate observable models or capture siblings', () => {
  assert.ok(check(`${home}browser/SearchBrowser.kt`, 'val state = MutableStateFlow(State())').length);
  assert.ok(check(`${home}browser/SearchBrowser.kt`, 'class SearchBrowser(val history: HistoryBrowser)').length);
  assert.ok(check(`${home}browser/SearchBrowser.kt`, 'import androidx.compose.runtime.Composable').length);
  assert.deepEqual(check(`${home}browser/BrowserContext.kt`, 'val state = MutableStateFlow(State())'), []);
});
test('browse wiring cannot absorb product pages or account mutations', () => {
  assert.ok(check(`${ui}browse/BrowseApplication.kt`, 'SettingsScreen()').length);
  assert.ok(check(`${ui}browse/BrowseApplication.kt`, 'runtime.connect(provider)').length);
  assert.deepEqual(check(`${ui}browse/BrowseApplication.kt`, 'SettingsEntryBinding(store)'), []);
});
test('responsibility budgets detect renewed concentration without measuring prose', () => {
  assert.ok(check(`${ui}App.kt`, Array(181).fill('val a = 1').join('\n')).length);
  assert.deepEqual(check(`${ui}App.kt`, Array(200).fill('// documented rule').join('\n')), []);
});
