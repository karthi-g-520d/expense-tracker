package com.karthi.expense.core

/**
 * Indian banks the SMS parser knows by name. The enum name is stored in the database and the
 * sheet (and is part of each transaction's id), so never rename an entry; add new ones instead.
 *
 * [senders] are the bank's DLT sender ids without the "AX-" prefix or "-S" suffix, e.g. an SMS from
 * "VM-HDFCBK-S" is HDFC. [names] are how the bank signs its messages, used when the sender is unknown.
 */
enum class Bank(val label: String, val senders: List<String>, val names: List<String>) {
    ICICI("ICICI", listOf("ICICIB", "ICICIT", "ICICIO", "ICICI"), listOf("ICICI")),
    IOB("IOB", listOf("IOBCHN", "IOBBNK", "IOBSMS", "IOB"), listOf("Indian Overseas Bank", "IOB")),
    SBI("SBI", listOf("SBIINB", "SBIUPI", "SBICRD", "ATMSBI", "CBSSBI", "SBIBK", "SBIBNK", "SBMSMS", "SBI"), listOf("State Bank of India", "SBI")),
    HDFC("HDFC", listOf("HDFCBK", "HDFCBN", "HDFCB", "HDFC"), listOf("HDFC")),
    AXIS("Axis", listOf("AXISBK", "AXISBN", "AXISMR", "AXISB"), listOf("Axis Bank")),
    KOTAK("Kotak", listOf("KOTAKB", "KOTAKD", "KOTAK", "KMBL"), listOf("Kotak")),
    PNB("PNB", listOf("PNBSMS", "PNBBNK", "PUNBNK", "PUNBN", "PNB"), listOf("Punjab National Bank", "PNB")),
    BOB("Bank of Baroda", listOf("BOBTXN", "BOBSMS", "BOBCRD", "BARODA", "BOB"), listOf("Bank of Baroda", "BOBCARD", "BOB")),
    CANARA("Canara", listOf("CANBNK", "CNRBNK", "CANARA"), listOf("Canara")),
    UNION("Union Bank", listOf("UNIONB", "UBOI", "UBINBK"), listOf("Union Bank")),
    BOI("Bank of India", listOf("BOIIND", "BOIBNK", "BOISMS"), listOf("Bank of India", "BOI")),
    CENTRAL("Central Bank", listOf("CENTBK", "CBOI", "CBIBNK"), listOf("Central Bank of India", "CBoI")),
    INDIAN("Indian Bank", listOf("INDBNK", "INDIANBK", "INDIAN"), listOf("Indian Bank", "IndianBank")),
    UCO("UCO", listOf("UCOBNK", "UCOBANK"), listOf("UCO Bank")),
    PSB("Punjab & Sind", listOf("PSBANK", "PSBNK"), listOf("Punjab&Sind Bank", "Punjab & Sind Bank")),
    IDBI("IDBI", listOf("IDBIBK", "IDBIBANK", "IDBI"), listOf("IDBI")),
    IDFC("IDFC First", listOf("IDFCFB", "IDFCBK", "IDFC"), listOf("IDFC FIRST", "IDFC")),
    YES("Yes Bank", listOf("YESBNK", "YESBK", "YESBANK"), listOf("YES BANK")),
    INDUSIND("IndusInd", listOf("INDUSB", "INDUSL", "INDUSIND"), listOf("IndusInd")),
    FEDERAL("Federal", listOf("FEDBNK", "FEDSMS", "FEDFIB", "FEDSCP", "FEDERAL"), listOf("Federal Bank")),
    SIB("South Indian Bank", listOf("SIBSMS", "SIBBNK", "SIBBANK"), listOf("South Indian Bank")),
    KVB("Karur Vysya", listOf("KVBANK", "KVBNK", "KVBSMS"), listOf("Karur Vysya", "KVB")),
    CUB("City Union", listOf("CUBANK", "CUBLTD", "CUBSMS"), listOf("City Union Bank", "CUB")),
    TMB("Tamilnad Mercantile", listOf("TMBANK", "TMBSMS", "TMBLTD"), listOf("Tamilnad Mercantile", "TMB")),
    KARNATAKA("Karnataka Bank", listOf("KBLBNK", "KTKBNK", "KTKBANK", "KARBANK"), listOf("Karnataka Bank")),
    AU("AU Bank", listOf("AUBANK", "AUSFBL"), listOf("AU Small Finance", "AU Bank")),
    EQUITAS("Equitas", listOf("EQUTAS", "EQUITA", "EQUITS"), listOf("Equitas")),
    BANDHAN("Bandhan", listOf("BANDHN", "BANDHAN"), listOf("Bandhan")),
    RBL("RBL", listOf("RBLBNK", "RBLBANK", "RBLCRD"), listOf("RBL Bank")),
    DBS("DBS", listOf("DBSBNK", "DBSBANK", "DIGIBK"), listOf("DBS")),
    HSBC("HSBC", listOf("HSBCIN", "HSBCBK", "HSBC"), listOf("HSBC")),
    SC("Standard Chartered", listOf("SCBANK", "STANCH", "SCBNK"), listOf("Standard Chartered", "StanChart")),
    CITI("Citi", listOf("CITIBK", "CITIBA"), listOf("Citibank")),
    PAYTM("Paytm Payments Bank", listOf("PAYTMB", "PYTMBK", "PAYTM"), listOf("Paytm Payments Bank", "Paytm Bank")),
    AIRTEL("Airtel Payments Bank", listOf("AIRBNK", "AIRTLB"), listOf("Airtel Payments Bank")),
    JIO("Jio Payments Bank", listOf("JIOPBS", "JIOPAY"), listOf("Jio Payments Bank")),
    IPPB("India Post Payments Bank", listOf("IPBMSG", "IPPBNK", "IPPB"), listOf("India Post Payments Bank", "IPPB")),
    FINO("Fino Payments Bank", listOf("FINOBK", "FINOPB"), listOf("Fino Payments Bank")),

    /** A bank-looking message whose bank we couldn't tell. */
    OTHER("Other bank", emptyList(), emptyList());

    companion object {

        /** Banks you can pick in Settings, in the order they're listed there. */
        val selectable: List<Bank> = entries.filter { it != OTHER }.sortedBy { it.label.lowercase() } + OTHER

        /** Short name for a stored bank name, e.g. "INDUSIND" -> "IndusInd"; unknown names are shown as is. */
        fun labelOf(name: String): String = entries.firstOrNull { it.name == name }?.label ?: name

        private val byName: List<Pair<Regex, Bank>> = entries
            .flatMap { b -> b.names.map { it to b } }
            .map { (name, bank) ->
                Regex("""(?<![A-Za-z])${Regex.escape(name)}(?![A-Za-z])""", RegexOption.IGNORE_CASE) to bank
            }

        /**
         * Which bank sent the SMS: by sender id when it has one we know (e.g. "JD-SBIUPI-S"),
         * otherwise by the bank's name in the text. Null when neither says.
         */
        fun detect(sender: String?, body: String): Bank? = fromSender(sender) ?: fromBody(body)

        fun fromSender(sender: String?): Bank? {
            if (sender.isNullOrBlank()) return null
            val parts = sender.uppercase().split('-').filter { it.isNotEmpty() }
            // "VM-HDFCBK-S" -> "HDFCBK"; a bare "HDFCBK" stays as it is.
            val id = parts.maxByOrNull { it.length } ?: return null
            if (id.all { it.isDigit() || it == '+' }) return null
            // Exact match first, then the longest known id the sender starts with.
            entries.firstOrNull { b -> b.senders.any { it == id } }?.let { return it }
            return entries
                .flatMap { b -> b.senders.map { it to b } }
                .filter { (code, _) -> code.length >= 3 && id.startsWith(code) }
                .maxByOrNull { it.first.length }
                ?.second
        }

        /**
         * Banks sign their SMS at the end ("...-Canara Bank") or the start ("BOI - ..."), and the middle
         * can name other banks ("NEFT from HDFC MUTUAL"), so a name at either end wins.
         */
        fun fromBody(body: String): Bank? {
            val found = byName.flatMap { (re, bank) -> re.findAll(body).map { it.range to bank } }
            // "Bank of India" inside "Central Bank of India" is not a separate name.
            val names = found.filter { (r, _) ->
                found.none { (o, _) -> o != r && o.first <= r.first && o.last >= r.last }
            }
            if (names.isEmpty()) return null
            return names.firstOrNull { (r, _) -> r.first <= 2 }?.second
                ?: names.filter { (r, _) -> r.last >= body.length - 25 }.maxByOrNull { it.first.last }?.second
                ?: names.minBy { it.first.first }.second
        }

        /** True for a sender that looks like a business id ("AX-HDFCBK") rather than a phone number. */
        fun isBusinessSender(sender: String?): Boolean =
            sender != null && sender.any { it.isLetter() }
    }
}
