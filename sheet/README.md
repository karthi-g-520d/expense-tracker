# Google Sheet setup

One-time setup, about 5 minutes. No Google Cloud project needed.

1. Create a new Google Sheet (e.g. "Expenses").
2. Open **Extensions > Apps Script**, delete the sample code, and paste in [`Code.gs`](Code.gs). Save.
3. In the function dropdown pick `setup` and click **Run**. Approve the permission prompt
   (it only touches this sheet). This creates the `Transactions` and `Monthly Summary` tabs.
4. Open **View > Logs** (or **Execution log**) and copy the token it printed.
5. Click **Deploy > New deployment**, type **Web app**:
   - Execute as: **Me**
   - Who has access: **Anyone**
   Click **Deploy** and copy the web app URL (ends in `/exec`).
6. In the project root, add to `local.properties` (this file is not committed):

   ```
   sheet.url=https://script.google.com/macros/s/XXXX/exec
   sheet.token=the-token-from-step-4
   ```

"Anyone" means the URL is reachable without a Google login, but every request must carry the
token, so keep both private. If the token ever leaks, delete the `TOKEN` script property and run
`setup` again to get a new one.

When you change `Code.gs` later, use **Deploy > Manage deployments > Edit > New version** so the
URL stays the same.
