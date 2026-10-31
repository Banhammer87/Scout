# Clearance Scout for Android

This project combines the Scout dashboard with a separate Android WebView for each retailer scan. Tap Scan all three retailers or choose a store. It visits public clearance category pages on your device, polls for rendered product cards, scrolls a few times, and returns the results to Scout. It scans loaded cards, not every page in the retailer's catalogue. Blocked or unreadable pages are reported. Dated examples are labelled. Export scan log uses Android's file picker.

## Build the installable test APK

The easiest build path on Windows without installing a local Android SDK is the included GitHub Actions workflow.

1. Extract this ZIP. Create a private GitHub repository and upload the contents of the extracted project at its root, including the hidden .github directory. Enable Actions if prompted.
2. Open Actions → Build Clearance Scout APK. A push runs the workflow, or choose Run workflow.
3. On a successful run, download the Clearance-Scout-APK artifact and unzip it. The installable file is app-debug.apk. Transfer it to the phone and open it to install. Android may request permission for the app opening the file to install unknown apps.
4. Open Clearance Scout, select Scan all three retailers, and inspect the timestamp and product counts. If the retailer doesn't load products, export the scan log.

For a local build, use JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0 and Gradle 8.9. From this directory run gradle :app:assembleDebug. The file is app/build/outputs/apk/debug/app-debug.apk. Verify it with the SDK's apksigner verify --verbose command.

Debug builds use a development signing key. Disposable CI runners can generate a different key for later builds. Keep a stable signing key for in-place updates.

## Scope

Retailer pages get Internet access and JavaScript but no native interface. Only the bundled Scout dashboard can invoke the native scan and export functions. Product links open externally when tapped. The app's Google Shopping action is a user-initiated external search. The existing hosted Scout handles verified online comparisons and requires the separately configured provider key for Google results. Automatic verified comparison inside this APK is not implemented.

This build has not been tested on a physical phone. Retailers may refuse WebView traffic or return layouts that do not expose product prices. No login or checkout is required for scanning.
