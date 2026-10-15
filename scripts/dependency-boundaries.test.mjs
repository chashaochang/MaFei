import { test } from 'node:test';
import assert from 'node:assert/strict';
import { boundaryViolations } from './verify-dependency-boundaries.mjs';

test('feature consumes repository, not a second SDK or platform bridge', () => {
  assert.deepEqual(boundaryViolations(':feature:home', 'import cn.mafei.cmp.data.repositories.MediaRepository'), []);
  assert.equal(boundaryViolations(':feature:home', 'val sdk = cn.mafei.cmp.providers.jellyfin.Facade()').length, 1);
  assert.equal(boundaryViolations(':feature:home', 'import cn.mafei.cmp.session.ApplicationRuntime').length, 1);
});
test('shared UI and player core cannot reverse-depend on features/data', () => {
  assert.equal(boundaryViolations(':common:ui', 'import cn.mafei.cmp.feature.home.HomeScreen').length, 1);
  assert.equal(boundaryViolations(':player:core', 'import cn.mafei.cmp.data.repositories.MediaRepository').length, 1);
  assert.deepEqual(boundaryViolations(':core:domain', '/* cn.mafei.cmp.feature.home.Example */\nclass Model'), []);
});
