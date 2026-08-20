import assert from 'node:assert/strict'
import test from 'node:test'
import { validateMergedProfile } from './verify_api26_native_material_build.mjs'

const validProfile = {
  app: {
    compileSdkVersion: '26.0.0.23',
    targetAPIVersion: 260000026,
    minAPIVersion: 60101024
  },
  module: {
    abilities: [
      {
        name: 'EntryAbility',
        metadata: [
          {
            name: 'configColorModeChangePerformanceInArkUI',
            value: 'true'
          }
        ]
      }
    ],
    extensionAbilities: [
      {
        name: 'EntryBackupAbility',
        metadata: [
          { name: 'ohos.extension.backup', value: 'true' }
        ]
      }
    ]
  }
}

test('accepts API 26 DEFAULT material configuration', () => {
  assert.doesNotThrow(() => validateMergedProfile(validProfile))
})

test('rejects the shortened API 26 value not emitted by local Hvigor', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      app: { ...validProfile.app, targetAPIVersion: 26000000 }
    }),
    /target API must equal 260000026/
  )
})

test('rejects a malformed API 26 compile SDK', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      app: { ...validProfile.app, compileSdkVersion: '26.0.00-invalid' }
    }),
    /compile SDK must be 26\.0\.0/
  )
})

test('rejects a non-string compile SDK that looks valid after coercion', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      app: { ...validProfile.app, compileSdkVersion: ['26.0.0.23'] }
    }),
    /compile SDK must be 26\.0\.0/
  )
})

test('rejects a minimum below API 24', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      app: { ...validProfile.app, minAPIVersion: 60000020 }
    }),
    /minimum API must remain 6\.1\.1\(24\)/
  )
})

const invalidApiFields = [
  ['target API string', 'targetAPIVersion', '260000026', /target API must equal 260000026/],
  ['target API array', 'targetAPIVersion', [260000026], /target API must equal 260000026/],
  ['minimum API string', 'minAPIVersion', '60101024', /minimum API must remain 6\.1\.1\(24\)/],
  ['minimum API array', 'minAPIVersion', [60101024], /minimum API must remain 6\.1\.1\(24\)/]
]

invalidApiFields.forEach(([name, field, value, expectedError]) => {
  test(`rejects ${name}`, () => {
    assert.throws(
      () => validateMergedProfile({
        ...validProfile,
        app: { ...validProfile.app, [field]: value }
      }),
      expectedError
    )
  })
})

test('rejects explicit UIMaterial state metadata on an ability', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      module: {
        ...validProfile.module,
        abilities: [
          {
            ...validProfile.module.abilities[0],
            metadata: [
              { name: 'ohos.arkui.UIMaterial.state', value: 'enable' }
            ]
          }
        ]
      }
    }),
    /must remain absent/
  )
})

test('rejects explicit UIMaterial state metadata on an extension ability', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      module: {
        ...validProfile.module,
        extensionAbilities: [
          {
            ...validProfile.module.extensionAbilities[0],
            metadata: [
              { name: 'ohos.arkui.UIMaterial.state', value: 'disable' }
            ]
          }
        ]
      }
    }),
    /must remain absent/
  )
})

test('rejects UIMaterial metadata in a deeply nested merged section', () => {
  assert.throws(
    () => validateMergedProfile({
      ...validProfile,
      generated: {
        mergedLayers: [
          {
            payload: {
              metadata: [
                { name: 'ohos.arkui.UIMaterial.state', value: 'enable' }
              ]
            }
          }
        ]
      }
    }),
    /must remain absent/
  )
})

const invalidMetadataValues = [
  ['object', { name: 'ohos.arkui.UIMaterial.state', value: 'enable' }],
  ['null', null],
  ['string', 'ohos.arkui.UIMaterial.state']
]

invalidMetadataValues.forEach(([shape, metadata]) => {
  test(`rejects ${shape}-shaped metadata`, () => {
    assert.throws(
      () => validateMergedProfile({
        ...validProfile,
        module: {
          ...validProfile.module,
          abilities: [
            {
              ...validProfile.module.abilities[0],
              metadata
            }
          ]
        }
      }),
      /metadata must be an array/
    )
  })
})
