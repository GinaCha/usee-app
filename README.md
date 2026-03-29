# usee-app

This workspace contains a minimal Android app scaffold with two product flavors: `dev` and `prd`.

Build and run
 - Build dev debug APK: `./gradlew :app:assembleDevDebug`
 - Build prd release APK: `./gradlew :app:assemblePrdRelease`

Secrets
 - Put your secret values in `app/src/main/assets/secrets.properties` (this file is gitignored).

The app shows the active flavor (`BuildConfig.ENV_NAME`) and loads `SECRET_API_KEY` from `secrets.properties`.

Using flavor-specific values
- Access the flavor base URL in Kotlin/Java: `val baseUrl = BuildConfig.BASE_URL`

Recommended project structure for flavor separation:

app/
 └── src/
	 ├── dev/
	 │   └── java/... (dev-only code)
	 │   └── res/mipmap-anydpi-v26/ (dev icons)
	 ├── prd/
	 │   └── java/... (prd-only code)
	 │   └── res/mipmap-anydpi-v26/ (prd icons)
	 └── main/

I added simple flavor-specific adaptive launcher icons and color resources under `app/src/dev/res` and `app/src/prd/res`. Replace the foreground drawables or supply PNGs in `mipmap` folders for production-quality icons.
