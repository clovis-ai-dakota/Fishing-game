# Turkish glossary (values-tr)

The choices behind Deep Line's Turkish text, so every screen, the tour and the store listing
use the same words. Follow `docs/localization.md` first; this file only records the Turkish
decisions.

## Register and tone

- **Sen** (informal "you") everywhere, as in the store listing: imperatives in the second person
  singular ("Oltayı at", "Yakala"), possessives in -(i)n ("Halatın", "Teknen").
- Casual and warm, like one angler talking to another. "Rastgele!" (the anglers' traditional
  wish for good fishing) closes the tour.
- Legal-ish texts (privacy, account deletion) stay plain and exact; "İzin ver" / "İzin verme"
  are equal choices.

## Game terms

| English | Turkish | Notes |
| --- | --- | --- |
| Deep Line | Deep Line | Never translated. Suffixes after an apostrophe as pronounced: Deep Line'a, 'da, 'ı |
| Fish (tab) | Balık | |
| Dex | Dex | Kept. Dex'e, Dex'te, Dex'in |
| Shop | Mağaza | A section name: Mağaza'da, Mağaza'dan |
| Battle (tab), battle | Düello | Also the listing's word. "1v1 Düello" |
| Species / Seen / Caught | Tür / Görüldü (Görülen) / Yakalandı (Yakalanan) | |
| Cast (noun) | Atış | "solo atış", "Atış: 12" |
| Cast (verb), Cast line | Olta atmak, **Oltayı at** | The main button |
| Hook | İğne | Also the skin slot |
| Line | Misina | The fishing line and the skin slot |
| Rope | **Halat** | Upgradable: Halat Uzunluğu / Gücü / Çevikliği (short: Uzunluk, Güç, Çeviklik). Never "misina" |
| Bump, bump-free | Çarpma, çarpmasız | "4 çarpma hakkı", "Çarpmasız bölge" |
| Head | Kafa | |
| Load | Yük | The weight bar |
| Reel in fast | Hızlı sar | |
| Tackle | Ekipman | Shop page and items |
| Lantern | Fener (capitalised) | Plural after a number stays singular: "3 Fener" |
| Rare fish detector / Detector | Nadir balık dedektörü / Dedektör | |
| Lucky charm | Şans tılsımı | |
| Sparkle lure | Parıltı yemi | |
| Sparkling | Parıldayan (capitalised) | Never used for anything else (other shines: ışıldayan, parlayan) |
| Sunlit Shallows / Twilight Reef / Midnight Trench / Abyssal Floor | Güneşli Sığlıklar / Alacakaranlık Resifi / Gece Yarısı Çukuru / Abis Tabanı | Short: Sığlıklar, Resif, Çukur, Taban |
| Driftwood / Rock / Jelly cluster | Kütük / Kaya / Denizanası kümesi | |
| Points, pts | Puan (unit "puan") | "1.250 puan" |
| Milestones | Kilometre taşları | |
| Fishing secrets / Secrets | Balıkçılık sırları / Sırlar | |
| Skins / looks | Görünümler / görünüm | |
| Title, title badge, Dex cards | Unvan, unvan rozeti, Dex kartları | |
| Atmosphere: Family, Nightmare | Atmosfer: Aile, Kâbus | |
| Themes; Day, Night, Like my phone | Temalar; Gündüz, Gece, Telefonuma göre | |
| Shop pages Rope, Tackle, Style, Passes, Aquarium | Halat, Ekipman, Stil, Biletler, Akvaryum | |
| Captain's Club | Kaptan Kulübü | |
| Season pass / Collector's edition / Weekend event (pass) / Starter pack / Founder's pack | Sezon bileti / Koleksiyoncu sürümü / Hafta sonu etkinliği (etkinlik bileti) / Başlangıç paketi / Kurucu paketi | |
| Season tier | Kademe | Secret tiers too ("Kademe 3"), Legendary = Efsanevi |
| Best of 5 / 3-Minute Rush / Heavy Haul / Species Hunt / Deep Dive | 5'te En İyi / 3 Dakika Yarışı / Ağır Av / Tür Avı / Derin Dalış | "5'te en iyi" is whitelisted in `StoreListingTest` |
| Easy, Normal, Hard; Rival | Kolay, Normal, Zor; Rakip | Levels are adjectives: "Zor rakibe karşı" |
| Round | Tur | The guided tour is "tanıtım", never "tur" |
| Forfeit, by forfeit | Pes etmek, hükmen | |
| Quick match, room code | Hızlı eşleşme, oda kodu | |
| Wager, stake | Bahis (puan yatırmak) | |
| Gift, cloud save, backup, recovery code | Hediye, bulut kaydı, yedek, kurtarma kodu | |
| Player id, friend code | Oyuncu kimliği, arkadaş kodu | |
| Daily reward, streak, streak shield | Günlük ödül, seri, seri kalkanı | |
| Daily challenges, swap | Günlük görevler, değiştirme (hakkı) | |
| Migration | Göç | |
| Friends and rankings, leaderboards, board | Arkadaşlar ve sıralamalar, sıralamalar, tablo | |
| Best cast / Deepest catch / Rarest catch; Everyone / League / Friends | En iyi atış / En derin av / En nadir av; Herkes / Lig / Arkadaşlar | |
| Weekly tournament, attempt | Haftalık turnuva, deneme | |
| Club, owner, member, club goal | Kulüp, **Başkan**, Üye, kulüp hedefi | "Başkan" reads naturally for a club owner |
| Online / offline | Çevrim içi / çevrim dışı | |
| Free | Ücretsiz | One word everywhere (not "bedava") |
| Angler | Balıkçı | |
| Fish log | Balık defteri | |
| Level (rope), stat | Seviye (short "Sv."), özellik | |

Secret titles are catchy move names (Serbest geçiş, Pinbol, Dibe vuruş, Son saniye, Köşe
vuruşu, Ezici zafer, Büyük vurgun, Davut ile Golyat...): see `core_secret_*`. They appear only
where English shows them.

## Names and numbers in sentences (suffixes)

Turkish suffixes follow the last vowel of the word, so a placeholder can't carry one reliably.

- **Names** (players, clubs, species, zones, items, modes) go where no suffix is needed: as the
  subject ("%1$s kazandı!"), after a colon ("Artık arkadaşsınız: %1$s."), in parentheses, or
  before a postposition or a noun that carries the suffix: "%1$s için", "%2$s adlı oyuncuya",
  "%1$s bölgesinde", "%1$s modunu", "%1$s türünü", "%1$s kulübünden", "%1$s kodlu".
- **Numbers**: the suffix goes on the noun after them, never on the digit: "%1$d kez",
  "%1$d dakikalık", "%1$d balıklı", "%1$d karakterlik". A unit symbol may take the suffix as
  read aloud (m = metre): "%1$d m'ye", "%1$d m'den".
- **Durations** ("2 g 5 sa") take postpositions: "%1$s sonra", "%1$s boyunca", "her %1$s sonunda".
- **Clock times** ("21:00"): "%1$s ile %2$s arasında", "Saat %1$s itibarıyla".
- **Ordinals**: "%1$d. gün", "%1$d. kademe"; tournament ranks come from ICU already as "3.".
- **Percent** goes before the number: "%%%1$d" gives "%45", "-%%%1$d" gives "-%25"; literal
  texts write "İlk %10".
- Units keep their symbols with a space: m, kg, m/s; short durations g, sa, dk; compact numbers
  "5 B" (bin), "1,5 Mn" (milyon).

## Plurals

Turkish has `one` and `other`, and a noun stays singular after a number ("3 balık", "5 gün"), so
both forms are usually identical: write both anyway. Only a few differ in the rest of the
sentence (e.g. `daily_swaps_left`: "bir görev" / "görevler").

## Capitalisation

Never change case in code (dotted İ/i and dotless I/ı). Game names keep their capitals in
running text (Fener, Parıldayan, Halat Uzunluğu, Kaptan Kulübü); ordinary words follow Turkish
sentence case.
