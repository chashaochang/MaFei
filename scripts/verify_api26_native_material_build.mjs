import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

function collectMetadata(node, result = []) {
  if (Array.isArray(node)) {
    node.forEach((item) => collectMetadata(item, result))
    return result
  }
  if (!node || typeof node !== 'object') {
    return result
  }

  if (Object.prototype.hasOwnProperty.call(node, 'metadata')) {
    if (!Array.isArray(node.metadata)) {
      const metadataType = node.metadata === null ? 'null' : typeof node.metadata
      throw new Error(`metadata must be an array, got ${metadataType}`)
    }
    result.push(...node.metadata)
  }

  Object.entries(node).forEach(([key, value]) => {
    if (key === 'metadata') {
      node.metadata.forEach((item) => collectMetadata(item, result))
      return
    }
    collectMetadata(value, result)
  })
  return result
}

export function validateMergedProfile(profile) {
  const compileSdkVersion = profile?.app?.compileSdkVersion
  const targetApiVersion = profile?.app?.targetAPIVersion
  const minApiVersion = profile?.app?.minAPIVersion
  const metadata = collectMetadata(profile)

  if (typeof compileSdkVersion !== 'string' || !/^26\.0\.0(?:\.\d+)?$/.test(compileSdkVersion)) {
    throw new Error(`compile SDK must be 26.0.0, got ${JSON.stringify(compileSdkVersion)}`)
  }
  if (typeof targetApiVersion !== 'number' ||
    !Number.isInteger(targetApiVersion) || targetApiVersion !== 260000026) {
    throw new Error(`target API must equal 260000026, got ${JSON.stringify(targetApiVersion)}`)
  }
  if (typeof minApiVersion !== 'number' ||
    !Number.isInteger(minApiVersion) || minApiVersion !== 60101024) {
    throw new Error(`minimum API must remain 6.1.1(24), got ${JSON.stringify(minApiVersion)}`)
  }
  if (metadata.some((item) => item?.name === 'ohos.arkui.UIMaterial.state')) {
    throw new Error('ohos.arkui.UIMaterial.state must remain absent for DEFAULT policy')
  }
}

function main() {
  const profilePath = process.argv[2] ??
    'entry/build/default/intermediates/merge_profile/default/module.json'
  const profile = JSON.parse(readFileSync(profilePath, 'utf8'))
  validateMergedProfile(profile)
  process.stdout.write('API 26 native material build profile verified\n')
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main()
}
