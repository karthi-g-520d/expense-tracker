# Expense Tracker

Android app that reads bank SMS from any Indian bank, lets you tag each debit or credit with a
category and reason, syncs them to a Google Sheet, and shows a monthly spending dashboard.

## Modules

- `core/` – plain Kotlin: SMS parsing (`SmsParser`, `GeneralSms` for any bank, `Bank` for sender ids) and the transaction model. Unit tested with sample SMS from ~25 banks (`BankSmsTest`).
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
