# Keyword notes (ASO)

How the listing texts were chosen. Research: `docs/research/launch.md` §7 and §10. Play has no
keyword field: it reads the **title** (most weight), the **short description** and the **full
description**. Write for people first, use each main term naturally, never a list of keywords.

## Main terms per language

| Locale | Title term | Other terms used | Why |
| --- | --- | --- | --- |
| en-US | deep sea fishing | fishing game, fish, species, collect, Dex, battles, tournament | "deep sea fishing" is both a common search and exactly the game; "fishing game" opens the description. |
| fr-FR | pêche en haute mer | jeu de pêche, poissons, espèces, collection, duels, tournoi | "haute mer" is the usual French for deep sea fishing; "jeu de pêche" in the first sentence. |
| es-ES | pesca en alta mar | juego de pesca, peces, especies, colección, duelos, torneo | Spain's wording ("móvil", "vosotros" in "pescáis"). For Latin America, copy to es-419 and change "móvil" to "celular", "pescáis" to "pescan". |
| de-DE | Tiefsee-Angeln | Angelspiel, Fische, Arten, sammeln, Duelle, Turnier | Germans search "Angelspiel" and "Angeln" rather than "Fischen"; du-form, as most games. |
| pt-BR | Pesca em Alto-Mar | jogo de pesca, peixes, espécies, colecionar, duelos, torneio | Brazilian Portuguese ("celular", "você", "online"). |
| tr-TR | Balık Tutma Oyunu | balık tutma oyunu, balık, tür, koleksiyon, düello, turnuva | "balık tutma oyunu" (fishing game) is the strongest Turkish search; the descriptive deep-sea phrase didn't fit in 30 characters. |

## Rules applied

- Brand first ("Deep Line"), then the descriptive term, all within 30 characters.
- The short description carries the loop (dive, dodge, hook on the way up) and "200" (a concrete
  hook); it isn't repeated in the full description.
- The first two lines of the full description explain the core loop: they're all most people
  read before "More".
- No ranking or performance claims in any language (`StoreListingTest` refuses "best", "#1",
  "top", "popular", "free" and their translations; mode names such as "Best of 5" are allowed).
- No promise the release doesn't keep: clubs are off in release builds (`FeatureFlags.clubs`), so
  the listing doesn't mention them yet. When they ship, add a line to "Every day, every week".
- Fishing secrets: the listing says they exist, never names or explains one (the test checks
  every secret's name).
- Numbers come from the game (200 species, 4 bumps, the dark at 300 m): the test checks them.

## Later

- Try title variants with a store listing experiment once there's traffic: "Deep Line: Fishing
  Adventure", "Deep Line: Fish Collector". One change at a time, at least 7 days.
- Custom store listings per country (Play Console) if one market responds well.
- Add Japanese, Korean and Italian after launch if analytics show players there.
