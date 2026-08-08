import assert from 'node:assert/strict'
import test from 'node:test'
import {
  validateEasyGoContracts,
  validateWorkspaceEasyGoContracts
} from './verify_easygo_parallel_vision_contracts.mjs'

function validFixture() {
  return {
    moduleSource: '{ "module": { "easyGo": "$profile:easy_go" } }',
    easyGo: {
      tablet: {
        displayModeOptions: {
          wideWindowMode: 'navigationSplit',
          squareWindowMode: 'navigationSplit',
          navigationSplitOptions: {
            homePage: 'navBar',
            homeNavigationId: 'mainNavigation',
            fullScreenPages: ['PlayerPage', 'LiveTvPage'],
            enableReducedContainerSize: true,
            supportLandscapeFullscreen: true,
            wideSplit: { ratio: '1 | 2' },
            squareSplit: { ratio: '1 | 1' },
            mode: 1
          }
        }
      }
    },
    routerSource: [
      "static readonly PlayerPage = 'PlayerPage'",
      "static readonly LiveTvPage = 'LiveTvPage'"
    ].join('\n')
  }
}

test('accepts the approved EasyGo Navigation contract', () => {
  assert.doesNotThrow(() => validateEasyGoContracts(validFixture()))
})

test('rejects shopping mode because the left page must remain the home page', () => {
  const fixture = validFixture()
  fixture.easyGo.tablet.displayModeOptions.navigationSplitOptions.mode = 0
  assert.throws(() => validateEasyGoContracts(fixture), /mode must be navigation mode 1/)
})

test('rejects a player route that is not fullscreen', () => {
  const fixture = validFixture()
  fixture.easyGo.tablet.displayModeOptions.navigationSplitOptions.fullScreenPages = ['PlayerPage']
  assert.throws(() => validateEasyGoContracts(fixture), /LiveTvPage/)
})

test('rejects a mismatched root Navigation id', () => {
  const fixture = validFixture()
  fixture.easyGo.tablet.displayModeOptions.navigationSplitOptions.homeNavigationId = 'playerNavigation'
  assert.throws(() => validateEasyGoContracts(fixture), /mainNavigation/)
})

test('workspace EasyGo configuration satisfies the contract', () => {
  assert.doesNotThrow(() => validateWorkspaceEasyGoContracts())
})
