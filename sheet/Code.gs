/**
 * Google Apps Script backend for the Expense Tracker app.
 * Paste into Extensions > Apps Script of your sheet. Setup steps are in sheet/README.md.
 *
 * POST {token, rows:[...]}  -> appends tagged transactions, skipping ids already present
 * GET  ?token=...           -> returns every transaction as JSON, for the dashboard
 */

const SHEET = 'Transactions';
const SUMMARY = 'Monthly Summary';
const HEADERS = ['id', 'date', 'type', 'amount', 'bank', 'account', 'counterparty',
                 'category', 'reason', 'reference', 'balance', 'synced_at'];

/** Run once from the editor: creates the tabs and a random token. */
function setup() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SHEET) || ss.insertSheet(SHEET);
  sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
  sheet.setFrozenRows(1);
  sheet.getRange('B:B').setNumberFormat('yyyy-mm-dd');
  sheet.getRange('D:D').setNumberFormat('#,##0.00');

  // Spending per month and category, newest month first, like a usage statement.
  const summary = ss.getSheetByName(SUMMARY) || ss.insertSheet(SUMMARY);
  summary.clear();
  summary.getRange('A1').setFormula(
    "=QUERY({ARRAYFORMULA(IF(ISNUMBER(" + SHEET + "!B:B),TEXT(" + SHEET + "!B:B,\"yyyy-mm\"),)), " +
    SHEET + "!C:C, " + SHEET + "!D:D, " + SHEET + "!H:H}, " +
    "\"select Col1, Col2, Col4, sum(Col3) where Col1 is not null " +
    "group by Col1, Col2, Col4 order by Col1 desc, Col2 desc, sum(Col3) desc " +
    "label Col1 'Month', Col2 'Type', Col4 'Category', sum(Col3) 'Total'\", 0)");

  const props = PropertiesService.getScriptProperties();
  if (!props.getProperty('TOKEN')) props.setProperty('TOKEN', Utilities.getUuid());
  Logger.log('Your token (put it in local.properties as sheet.token): ' + props.getProperty('TOKEN'));
}

function doPost(e) {
  const body = JSON.parse(e.postData.contents);
  if (!authorized(body.token)) return json({ error: 'unauthorized' });

  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET);
    const last = sheet.getLastRow();
    const existing = new Set(last > 1 ? sheet.getRange(2, 1, last - 1, 1).getValues().flat() : []);
    const now = new Date();
    const rows = (body.rows || [])
      .filter(r => !existing.has(r.id))
      .map(r => [r.id, toDate(r.date), r.type, Number(r.amount), r.bank, r.account,
                 r.counterparty || '', r.category || '', r.reason || '', r.reference || '',
                 r.balance == null ? '' : Number(r.balance), now]);
    if (rows.length) sheet.getRange(last + 1, 1, rows.length, HEADERS.length).setValues(rows);
    // The app marks every sent id as synced, including ones that were already in the sheet.
    return json({ added: rows.length, ids: (body.rows || []).map(r => r.id) });
  } finally {
    lock.releaseLock();
  }
}

function doGet(e) {
  if (!authorized(e.parameter.token)) return json({ error: 'unauthorized' });
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getSheetByName(SHEET);
  const values = sheet.getDataRange().getValues();
  const rows = values.slice(1).filter(v => v[0] !== '').map(v => {
    const o = {};
    HEADERS.forEach((h, i) => o[h] = v[i] instanceof Date
      ? Utilities.formatDate(v[i], Session.getScriptTimeZone(), h === 'date' ? 'yyyy-MM-dd' : "yyyy-MM-dd'T'HH:mm:ss")
      : v[i]);
    return o;
  });
  return json({ rows: rows, sheetUrl: SpreadsheetApp.getActiveSpreadsheet().getUrl() });
}

function authorized(token) {
  return token && token === PropertiesService.getScriptProperties().getProperty('TOKEN');
}

// "2026-10-20" -> Date in the sheet's time zone, so month grouping works.
function toDate(s) {
  const [y, m, d] = s.split('-').map(Number);
  return new Date(y, m - 1, d);
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
