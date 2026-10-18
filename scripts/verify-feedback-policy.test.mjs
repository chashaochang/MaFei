import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { feedbackSources, feedbackViolations as check, verifyFeedbackPolicy } from './verify-feedback-policy.mjs';

const rules = (source, file) => check(source, file).map(issue => issue.rule);

test('removed APIs cannot return through imports, aliases, qualified calls or declarations', () => {
  for (const source of [
    'InlineProblem(error)',
    'cn.mafei.cmp.common.ui.MaFeiInlineNotice(message)',
    'import cn.mafei.cmp.common.ui.InlineProblem as Harmless',
    'typealias Notice = MaFeiInlineNotice',
    'fun MaFeiInlineNotice(message: String) {}',
    'val renderer = ::InlineProblem',
    'fun `InlineProblem`() {}',
  ]) assert.ok(rules(source).includes('removed-inline-api'), source);
});

test('direct error Text is rejected across multiline, named arguments and qualified renderer calls', () => {
  for (const source of [
    'Text(error)', 'Text(state.permissionError)', 'Text(error ?: "Try again")',
    'Text(\n modifier = Modifier,\n text = state.failureMessage\n)',
    'androidx.compose.material3.Text(errorMessage)',
    'Text("Invalid", color = MaterialTheme.colorScheme.error)',
    'BasicText(problem)', 'Text(this.errorMessage)',
  ]) assert.ok(rules(source).includes('direct-error-text'), source);
});

test('nullable let implicit and explicit aliases cannot hide inline feedback', () => {
  for (const source of [
    'error?.let { Text(it) }',
    'state.connectionError?.let { message -> Text(message) }',
    'state.error?.let { problem -> Column { BasicText(problem) } }',
    'error?.let { val message = it\n Text(message) }',
  ]) assert.ok(rules(source).includes('direct-error-text'), source);
  assert.deepEqual(check('error?.let { save(it) }; value?.let { Text(it) }'), []);
});

test('renderer imports and straightforward local value aliases retain error detection', () => {
  assert.ok(rules('import androidx.compose.material3.Text as StatusText\nStatusText(error)').includes('direct-error-text'));
  assert.ok(rules('fun View() {\n val message = state.error\n Text(message)\n}').includes('direct-error-text'));
  assert.deepEqual(check('fun save() { val message = state.error\n log(message) }; fun View() { Text(message) }'), []);
});

test('explicitly typed local aliases preserve error propagation without treating the type as an error value', () => {
  for (const source of [
    'val message: String = error\nText(message)',
    'fun View() { val message: String? = state.error; Text(message.orEmpty()) }',
    'error?.let { val message: String = it\n Text(message) }',
    'val message: String = error\nval label: String = message\nText(label)',
  ]) assert.ok(rules(source).includes('direct-error-text'), source);
  assert.deepEqual(check('val label: ErrorDescription = savedLabel\nText(label)'), []);
  assert.deepEqual(check('fun A() { val message: String = error\nlog(message) }; fun B() { Text(message) }'), []);
});

test('nested lambda bindings shadow outer error aliases only inside their own scope', () => {
  for (const source of [
    'error?.let { items.forEach { Text(it.title) } }',
    'error?.let { value.let { Text(it.title) } }',
    'error?.let { items.map { Text(it.title) } }',
    'error?.let { items.forEach { it -> Text(it.title) } }',
    'error?.let { message -> items.forEach { message -> Text(message.title) } }',
    'error?.let { items.forEach { val label: String = it.title\n Text(label) } }',
  ]) assert.deepEqual(check(source), [], source);
  for (const source of [
    'error?.let { Column { Text(it) } }',
    'error?.let { run { Text(it) } }',
    'error?.let { items.forEach { value -> Text(it) } }',
    'items.forEach { error?.let { Text(it) } }',
    'error?.let { items.forEach { Text(it.title) }\n Text(it) }',
    'error?.let { message -> items.forEach { Text(message) } }',
  ]) assert.deepEqual(rules(source), ['direct-error-text'], source);
});

test('renaming the removed surface to another obvious inline error component is rejected', () => {
  for (const source of ['ErrorBanner(message)', 'InlineError(message)', 'FailureNotice(message)', 'fun ConnectionErrorRow() {}']) {
    assert.ok(rules(source).includes('inline-error-component'), source);
  }
  assert.deepEqual(check(`
    val browserError = null
    val browseErrorMessage = state.error
    val detailSectionsError = null
    val sectionsError = null
    fun throwError() {}
    fun errorSection() {}
    napi_throw_error(code)
    ASSET_CONFLICT_THROW_ERROR
    val value = ErrorBannerState(error)
    fun connectionFailure() {}
  `), []);
});

test('comments and literal strings cannot trigger policy errors or authorize exceptions', () => {
  assert.deepEqual(check(`// InlineProblem(error)\n/* MaFeiInlineNotice(error) /* nested */ */\nText("InlineProblem(error)")\nval docs = """Text(error) // mafei-feedback: full-page-error -- not a comment"""`), []);
  assert.deepEqual(check('Text("https://example.test/error")\nText("escaped \\" error")'), []);
  assert.deepEqual(check('Text("$error") // literal in ArkTS', 'Screen.ets'), []);
});

test('interpolation is executable and cannot hide error text inside strings', () => {
  for (const source of ['Text("Problem: $error")', 'Text("Problem: ${state.error}")', 'Text("""Problem: ${state.error ?: "none"}""")']) {
    assert.ok(rules(source).includes('direct-error-text'), source);
  }
  assert.ok(rules('Text(`Problem: ${this.errorMessage}`)', 'Screen.ets').includes('direct-error-text'));
  assert.deepEqual(check('Text("escaped \\$error")'), []);
});

test('approved standard feedback, normal content and non-rendering error logic remain valid', () => {
  assert.deepEqual(check(`
    MaFeiErrorDialog(message = error)
    MaFeiGlassConfirmDialog(content = error)
    LegacyTransientToast(message = error)
    LegacyFailedView(message = error)
    val error = response.error
    logger.error(error)
    Text(title)
    Text("Error is a word in documentation")
  `), []);
});

test('documented exceptions cover exactly one rendering, not a file or content subtree', () => {
  for (const category of ['full-page-error', 'dialog-content', 'business-history', 'diagnostic-state']) {
    assert.deepEqual(check(`// mafei-feedback: ${category} -- This is a specific reviewed rendering reason.\nText(error)`), []);
    assert.deepEqual(rules(`// mafei-feedback: ${category} -- This is a specific reviewed rendering reason.\nText(error)\nText(otherError)`), ['direct-error-text']);
    assert.deepEqual(rules(`// mafei-feedback: ${category} -- This is a specific reviewed rendering reason.\nText(error); Text(otherError)`), ['direct-error-text']);
  }
  assert.ok(rules('// mafei-feedback: full-page-error -- This is a specific reviewed rendering reason.\nInlineProblem(error)').includes('removed-inline-api'));
  assert.ok(rules('// mafei-feedback: full-page-error -- This is a specific reviewed rendering reason.\nErrorBanner(error)').includes('inline-error-component'));
});

test('broad, stale, unknown or unexplained exception markers fail closed', () => {
  assert.ok(rules('// mafei-feedback: allow-all -- Ignore everything in this file.\nText(error)').includes('invalid-exception'));
  assert.ok(rules('// mafei-feedback: full-page-error -- ok\nText(error)').includes('invalid-exception'));
  assert.ok(rules('// mafei-feedback: full-page-error -- This is a specific reviewed rendering reason.\nColumn {\n Text(error)\n}').includes('unused-exception'));
  assert.ok(rules('// mafei-feedback: dialog-content -- This is a specific reviewed rendering reason.\nText(title)').includes('unused-exception'));
  assert.ok(rules('val value = 1 // mafei-feedback: full-page-error -- This is a specific reviewed rendering reason.\nText(error)').includes('invalid-exception'));
});

test('source scan covers all active hosts and excludes third-party, generated and test code', () => {
  const root = mkdtempSync(join(tmpdir(), 'mafei-feedback-'));
  try {
    const included = [
      'common/ui/src/sharedUiMain/kotlin/Feedback.kt',
      'feature/connect/src/sharedUiMain/kotlin/Connect.kt',
      'player/ui/src/sharedUiMain/kotlin/Player.kt',
      'composeApp/src/androidMain/kotlin/Host.kt',
      'platform/example/src/iosMain/kotlin/Host.kt',
      'harmonyApp/entry/src/main/ets/pages/Index.ets',
    ];
    const excluded = [
      'legacy/harmony-arkts-reference/entry/src/main/ets/Old.ets',
      'common/glass/vendor/library/src/sharedUiMain/kotlin/Vendor.kt',
      'feature/connect/build/src/sharedUiMain/kotlin/Generated.kt',
      'feature/connect/src/desktopTest/kotlin/Fixture.kt',
      'feature/connect/src/sharedUiMain/kotlin/generated/Generated.kt',
      'harmonyApp/entry/src/ohosTest/ets/Test.ets',
      'harmonyApp/oh_modules/library/src/main/ets/Library.ets',
      'harmonyApp/entry/src/test/ets/Test.ets',
      'feature/connect/src/sharedUiMain/kotlin/ViewTest.kt',
    ];
    for (const path of [...included, ...excluded]) {
      mkdirSync(dirname(join(root, path)), { recursive: true });
      writeFileSync(join(root, path), 'InlineProblem(error)\n');
    }
    assert.deepEqual(Object.keys(feedbackSources(root)).sort(), included.sort());
    assert.throws(() => verifyFeedbackPolicy(root), /Index\.ets:1 \[removed-inline-api\]/);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
