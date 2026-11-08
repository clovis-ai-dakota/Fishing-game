# Target audience and content: proposed answers (decision needed)

For Play Console > **Policy > App content > Target audience and content**. Research:
`docs/research/analytics-privacy.md` §8 and `docs/research/launch.md` §9.

> **Decision for the developer: the Families question.** Everything below assumes Deep Line is
> **not designed for children** and targets **13+**. Confirm it before submitting: the answers
> decide whether Google Play's Families policies apply (approved SDKs only, no Firebase Analytics
> for children without a neutral age screen, teacher-approved programme rules, and more).

## Proposed answers

| Question | Proposed answer | Notes |
| --- | --- | --- |
| Target age groups | **13-15, 16-17, 18 and over** | Not 12 and under. See the France note below: **16-17 and 18+ only** is the simplest legal choice. |
| Could the store listing unintentionally appeal to children? | **No**, if the listing stays as written | Google reviews the icon, screenshots and text. The art is cute and colourful: keep the words aimed at collectors and competitive players (depth, rare species, battles, rankings), never "for kids", no nursery-style captions. If Google disagrees, it asks to change the listing, the audience, or add a "not for children" note. |
| Is the app primarily child-directed? | **No** | |
| Ads | **No ads** (also App content > Ads: "No, my app does not contain ads") | |
| Teacher Approved programme | Not applicable | Only for apps targeting children. |

## France and the age of digital consent (15)

In France a minor can consent alone to an online service from 15 (Loi Informatique et Libertés,
art. 45). Usage statistics and crash reports are opt-in in Deep Line, so a 13- or 14-year-old's
"Allow" isn't valid consent without a parent. Options:

1. **Target 16+ only** (16-17, 18+): simplest; small loss of reach.
2. Keep 13+ and add a neutral age question before the consent sheet that keeps both switches off
   (and skips the sheet) under 15. Needs a small app change and a privacy policy update.
3. Keep 13+ as is and accept the residual risk (pseudonymous, ad-free statistics, off by default,
   the privacy policy asks under-15s to leave them off without a parent).

Recommendation: option 1 at launch, or option 2 if younger teens matter for the game.

## Related declarations

- **App access**: all features are available without a login; no special instructions needed
  (the online features need the game server to be up during review).
- **Advertising ID**: the app doesn't use it (`AD_ID` permission removed).
- **Government apps, financial features, health, news**: No.
- **Data safety**: `docs/store/data-safety.md`.
