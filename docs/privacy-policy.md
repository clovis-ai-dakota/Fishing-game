# Deep Line privacy policy (draft)

> **Draft for the developer.** Fill in everything in [square brackets], check that it still
> matches what the app and the server do (see `docs/research/analytics-privacy.md` and
> `docs/analytics-events.md`), have it reviewed, then publish it on a public web page and build
> release apps with `-PdeeplinePrivacyPolicyUrl=<that page>`. Keep it consistent with the Play
> Data safety form (`docs/play-data-safety.md`). Publish a French version too: most of your
> players in France will expect one.

*Last updated: [date]*

Deep Line ("the game") is a fishing game for Android made by [your name or company], [postal
address], [country] ("we"). We are the data controller for the personal data described below.
Contact for anything about your data: **[privacy email address]**.

We built the game to need as little data as possible: no ads, no advertising ID, no accounts with
your name or email, and no statistics or crash reports unless you allow them.

## 1. What stays on your phone

Your progress (fish caught, points, upgrades, looks), your settings (sound, theme, your privacy
choices) and the name you type for online battles are saved on your phone only. Uninstalling the
game, or clearing its storage, deletes them. Android's own device backup may copy them to your
Google account if you use it (Google's backup, not ours).

## 2. Online features and the game server

The game talks to our game server (hosted by [hosting provider, country]) for online battles,
gifts and cloud save.

- **Your player ID.** On first launch the game creates a random player ID and a secret. They
  aren't linked to your name, email or phone. The game uses them when it checks for gifts (each
  time it starts), in online battles, and for cloud save. The server keeps the ID and a hash of the
  secret (never the secret itself). Your **recovery code** (Shop > Account) combines both: keep it
  private.
- **Online battles.** The server receives the battle name you choose, your title, your title
  badge's frame, your player ID and your casts, and passes the name, title, frame and ID to your
  opponent for the match. It doesn't store the match.
- **Gifts.** When someone sends you a gift, the server keeps it (the item, the sender's battle
  name and the date) until your game has collected it.
- **Cloud save** (Captain's Club members): a copy of your progress and when it was saved, so you
  can restore it on another phone with your recovery code.
- **Technical data.** Like any internet service, the server sees your IP address when the game
  connects. [We / our host] keep connection logs for [N days] for security; we don't use them for
  anything else.

**Legal basis**: providing the online features you use (performance of a contract, GDPR art.
6(1)(b)).

**How long**: until you delete your online account, [or after N months without using the game].

## 3. Usage statistics (only if you allow them)

If you allow usage statistics, the game sends **Google Analytics for Firebase** (Google Ireland
Limited / Google LLC) information about how the game is played: the screens you open, how your
casts and battles go (depth, catches, points, results), the tour, the points you earn and spend,
what you buy in the shop (the item, not your payment details), and a few broad descriptions such
as your rope's progress band and your menu theme. It's linked to a random app instance ID, never
to your name or player ID. Google also receives basic device information (model, Android version,
language, app version) and derives an approximate location (country, region) from your IP
address. We use it to fix problems and balance the game. It's never used for ads: the game has
none, and advertising features are switched off.

**Legal basis**: your consent (GDPR art. 6(1)(a) and article 82 of the French Loi Informatique et
Libertés). **Nothing is sent until you choose**, and you can change your mind at any time in
**Settings > Privacy**. Turning statistics off also clears the analytics data kept on your phone
and gives you a new random ID; **Reset my statistics ID** does the same while statistics stay on.

**How long**: Google Analytics keeps this data for [2 / 14] months, then deletes it.

## 4. Crash reports (only if you allow them)

If you allow crash reports, the game sends **Firebase Crashlytics** (Google) a report when it
crashes or when something fails (for example, the 3D graphics or a payment): what went wrong in
the code, the screen you were on, the device model and Android version, and a random installation
ID. We use them only to fix bugs.

**Legal basis**: your consent, changeable at any time in Settings > Privacy. If you refuse,
reports waiting on your phone are deleted without being sent.

**How long**: [90 days, per Firebase's documentation].

## 5. Purchases

Payments are handled by **Google Play**: we never see your card or bank details. Google tells the
game which item you bought and gives it a purchase token so it can deliver it; the game keeps the
tokens on your phone so nothing is delivered twice. Google's privacy policy applies to the
payment. If you allowed usage statistics and our Firebase project is linked to Google Play, Google
Analytics also receives your Google Play purchases (item, price, currency) to report revenue.

**Legal basis**: performance of the contract; we keep purchase records required by law for [10
years] (French accounting rules) [only if you receive such records].

## 6. Who receives data

- **Google** (Firebase Analytics, Firebase Crashlytics, Google Play), as our processor for
  analytics and crash reports, and for payments under its own terms.
- **[Hosting provider]**, which runs our game server.
- **Your online opponents**, who see your battle name, title, title frame and player ID during a
  match, and can send you gifts.

We don't sell your data and don't share it with advertisers.

**Transfers outside the EU**: Google may process data in the United States. Google LLC is
certified under the EU-US Data Privacy Framework (European Commission adequacy decision of 10 July
2023), and Google's data processing terms include the European Commission's standard contractual
clauses. [Hosting provider transfer, if any.]

## 7. Your rights

You can ask us to access, correct or delete your data, to restrict or object to its use, or to
receive it in a portable format, and you can withdraw your consent at any time (this doesn't
affect what was done before). Write to **[privacy email address]**; we answer within one month.

- **Delete your online account** in the game: **Settings > Privacy > Delete my online account**.
  It erases everything the server keeps for you (player ID, cloud save, gifts waiting) and the game
  starts with a new random ID. Your progress on the phone stays.
- **Without the game**: on **[https://your-server/delete-account]**, with your recovery code, or by
  writing to us with your battle name and roughly when you last played.
- **Statistics and crash reports**: switch them off in Settings > Privacy. Because they're linked
  only to random IDs, we can't find them from your name; tell us your app instance ID [how] if you
  want them erased before their retention period ends.

If you think we don't respect your rights, you can complain to the **CNIL** (Commission
Nationale de l'Informatique et des Libertés, 3 place de Fontenoy, TSA 80715, 75334 Paris Cedex 07,
[www.cnil.fr](https://www.cnil.fr)) or the data protection authority where you live.

## 8. Children

Deep Line is not designed for children under [13]. In France, players under 15 need a parent's
agreement to allow usage statistics or crash reports; if you're under 15, leave them off unless
a parent agrees. [Adjust to the audience you declare in Play Console: see the research notes,
section 8.]

## 9. Security

Connections to our server use encryption (HTTPS/WSS) [make sure the production server is behind
HTTPS]. The server keeps only a hash of your secret.

## 10. Changes

If this policy changes in a way that matters (for example, a new kind of data or a new company
receiving it), we'll update the date above and the game will ask for your choices again.
