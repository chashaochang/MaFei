# iPhone / iPad thin host

Open `MaFei.xcodeproj`, scheme **MaFei**. This is a UIKit scene/window host;
the default screen is shared Compose login/browsing, not a Swift rewrite. The
foundation probe remains under **实验室**. Each scene owns
its own Compose controller. Both device and simulator targets are ARM64 only.
The development bundle is `cn.xiaobai.mafei.cmp.dev`, separate from legacy.

The Xcode build phase runs `:composeApp:embedAndSignAppleFrameworkForXcode`,
which builds `MaFeiShared` and synchronizes Compose resources. Keep script
sandboxing disabled for this Gradle integration. Do not copy a framework from
another checkout or disable Compose's strict plist validation.

From the repository root (Xcode + JDK + configured Gradle SDK paths required):

```bash
xcodebuild -project iosApp/MaFei.xcodeproj -scheme MaFei \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/build/DerivedData CODE_SIGNING_ALLOWED=NO build
```

Install `iosApp/build/DerivedData/Build/Products/Debug-iphonesimulator/MaFei.app`
on an explicitly selected ARM simulator with `xcrun simctl install`, then
launch `cn.xiaobai.mafei.cmp.dev`. A returned PID is not proof of a first frame;
check a screenshot and the runtime log. Real devices need the user's signing
team/profile in Xcode; no credentials or release signing are committed here.

Run the shared scheme's native UI smoke test on an explicitly selected ARM
simulator (replace `SIMULATOR_UDID` using `xcrun simctl list devices`):

```bash
xcodebuild -project iosApp/MaFei.xcodeproj -scheme MaFei \
  -configuration Debug -destination 'platform=iOS Simulator,id=SIMULATOR_UDID' \
  -derivedDataPath iosApp/build/DerivedData \
  -resultBundlePath iosApp/build/ProbeSmoke.xcresult \
  CODE_SIGNING_ALLOWED=NO -parallel-testing-enabled NO test
```

Use a new result bundle path for each run. The test cold-launches only the dev
app, reads accessibility bounds before tapping, checks resource loading,
bar expansion, three material levels, dialog dismissal, rotation and
foreground restoration. Rotation is reset to portrait on exit. This does not
test process-death restoration, real GPU failure or physical-device performance.

`CADisableMinimumFrameDurationOnPhone=true` is required by Compose. The shared
screen owns safe-area consumption; do not add a second UIKit safe-area wrapper.
Icons/App Store packaging and production scene restoration remain release work.
GLM must not modify this directory.
