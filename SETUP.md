# Setup Instructions

This document provides instructions for setting up the Epsilon Music project for development.

## Prerequisites

- Android Studio (latest version recommended)
- Android SDK (API level as specified in `build.gradle.kts`)
- JDK 21
- Git

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/VihaanAngaria/EpsilonMusic.git
cd EpsilonMusic
```

### 2. Configure Local Properties

Create a `local.properties` file from the template:

```bash
cp local.properties.template local.properties
```

Edit `local.properties` and set your Android SDK path:

```properties
sdk.dir=/path/to/your/android/sdk
```

**Example paths:**

- macOS: `/Users/username/Library/Android/sdk`
- Linux: `/home/username/Android/sdk`
- Windows: `C:\\Users\\username\\AppData\\Local\\Android\\sdk`

### 3. Configure Firebase (Optional)

Firebase is used for analytics and crash reporting. If you want to use these features:

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app to your Firebase project with package name `com.epsilonmusic.app`
3. Download the `google-services.json` file
4. Place it in the `app/` directory (replacing the existing one)

**Important (post-rename):** The current `app/google-services.json` is configured for Firebase project `epsilonmusic-b7b95` with package `com.epsilonmusic.app` (release) and `com.epsilonmusic.app.debug` (debug). If you need to use a different Firebase project, replace the file with one downloaded from your own Firebase console.

**Firebase services used:**
- ✅ Firebase Analytics (GMS flavor only)
- ✅ Firebase Crashlytics (GMS flavor only)
- ❌ Firebase Authentication — NOT used (YouTube Music account sync handles authentication)
- ❌ Cloud Firestore — NOT used (local Room database + YouTube Music sync)
- ❌ Firebase Realtime Database — NOT used
- ❌ Firebase Storage — NOT used

**Note:** If you skip Firebase setup, the app will still build and run, but analytics and crash reporting will be disabled. To build without Firebase, simply delete `app/google-services.json` — the build system auto-detects its absence and skips the Google Services plugin.

### 4. Configure Release Signing (Optional)

For release builds, you need to configure signing credentials. Set these as environment variables or in `gradle.properties`:

```bash
# Environment variables
export KEYSTORE_PATH=/path/to/your/keystore.jks
export STORE_PASSWORD=your_store_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password
```

Or add to `gradle.properties` (never commit this file):

```properties
KEYSTORE_PATH=/path/to/your/keystore.jks
STORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

#### GitHub Actions Secrets (for CI release builds)

The `.github/workflows/android-build.yml` workflow builds a signed release APK on every push to `main`. The following GitHub repository secrets must be configured under **Settings → Secrets and variables → Actions**:

| Secret Name | Description | How to generate |
|---|---|---|
| `KEYSTORE_BASE64` | Your release keystore (`.jks`), base64-encoded | `base64 -i keystore.jks \| tr -d '\n'` |
| `KEY_ALIAS` | The alias of the signing key inside the keystore | Set when you created the keystore with `keytool` |
| `KEY_PASSWORD` | The password for the signing key | Set when you created the keystore |
| `STORE_PASSWORD` | The password for the keystore file itself | Set when you created the keystore |

**Never** commit the keystore file or passwords to the repository. The `.gitignore` already excludes `*.keystore` and `*.jks` files.

To generate a new release keystore locally:

```bash
keytool -genkeypair -v \
  -keystore keystore.jks \
  -alias epsilon-music \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <your-store-password> \
  -keypass <your-key-password>
```

Then base64-encode it and add it as the `KEYSTORE_BASE64` secret:

```bash
base64 -i keystore.jks | tr -d '\n' | pbcopy   # macOS
# or
base64 -w 0 keystore.jks                        # Linux
```

### 5. Build the Project

Open the project in Android Studio or build from the command line.

Epsilon Music now ships a single **GMS** build variant (with Google Cast support). The previous FOSS (no Google Play Services) variant has been removed.

```bash
# Debug build
./gradlew assembleUniversalGmsDebug

# Release build (requires signing configuration)
./gradlew assembleUniversalGmsRelease
```

*(On Windows, use `.\gradlew.bat` instead of `./gradlew`)*

### 6. Configure AI Translation (Optional)

Epsilon Music supports AI-powered lyrics translation. You can configure this in **Settings -> AI Settings**.

#### Option A: Using OpenRouter (Default)

This is the recommended setup for most users.

1. Get an API Key from [OpenRouter](https://openrouter.ai/).
2. In the app, go to **Settings -> AI Settings**.
3. Ensure **Provider** is set to **OpenRouter**.
4. Enter your **API Key**.

#### Option B: Using Custom Provider

Use this for other services like OpenAI, Anthropic, or local LLMs.

1. In the app, go to **Settings -> AI Settings**.
2. Select your **Provider** (e.g., ChatGPT, Gemini, or Custom).
3. If using **Custom**, enter your provider's **Base URL**.
4. Enter your **API Key**.

## Important Files

### Confidential Files (Never commit these)

- `local.properties` - Contains your local SDK path
- `app/google-services.json` - Contains Firebase credentials
- `*.keystore` - Contains signing keys for release builds
- `gradle.properties` - May contain signing credentials

These files are already listed in `.gitignore` and should never be committed to version control.

### Template Files (Safe to commit)

- `local.properties.template` - Template for local properties
- `app/google-services.json` - Optional Firebase configuration

## Troubleshooting

### Build Fails with "SDK location not found"

Make sure you've created `local.properties` with the correct SDK path.

### Firebase-related Build Errors

If you're not using Firebase, you can still build the standard debug variant without `app/google-services.json` — Firebase features will simply be disabled:

```bash
./gradlew assembleUniversalGmsDebug
```

### Gradle Sync Issues

Try cleaning and rebuilding:

```bash
./gradlew clean
./gradlew build
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.