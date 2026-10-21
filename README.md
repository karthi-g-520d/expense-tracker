# Expense Tracker

Personal Android app that reads ICICI and IOB bank SMS, lets you tag each debit or credit with a
category and reason, syncs them to a Google Sheet, and shows a monthly spending dashboard.

## Modules

- `core/` – plain Kotlin: SMS parsing (`SmsParser`) and the transaction model. Unit tested.
- `app/` – Android app (Kotlin, Jetpack Compose).
- `sheet/` – Google Apps Script that the app syncs with. See [sheet/README.md](sheet/README.md).

## Build

Open the folder in Android Studio and run the `app` configuration on your phone, or:

```
./gradlew :core:test        # parser tests
./gradlew :app:installDebug # install on a connected phone
```

Set up the sheet first and add `sheet.url` and `sheet.token` to `local.properties`.

## Roadmap

1. Project setup, sheet script, SMS parser ✅
2. Read new SMS since last run and store them on the phone ✅
3. Transaction cards (category + reason) and Sync button ✅
4. Dashboard from the sheet data, cached on the phone ✅
