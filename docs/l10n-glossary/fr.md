# French glossary (values-fr)

The words the French translation uses for Deep Line's terms, so every screen, the tour and the
store listing say the same thing. Follow it when you add or change a French string; the general
rules are in [`docs/localization.md`](../localization.md).

## Register and typography

- **Vous** everywhere, as in the store listing (`docs/store/listings/fr-FR/`): instructions in the
  imperative (*Pêchez 3 poissons.*), buttons in the infinitive (*Lancer la ligne*, *Récupérer*).
  Exception: text the player sends to a friend through the share sheet uses **tu**
  (`social_share_player_id`, `social_clubs_share`).
- No-break space (U+00A0) before `:` `;` `!` `?`, inside « », between a number and its unit
  (`12 m`, `5 kg`, `300 pts`, `2 h 5 min`, `45 %`) and before `×` when a space precedes it.
- Apostrophes are straight and escaped (`l\'eau`). Percentages are `45 %` (`%1$d %%` with
  arguments). Hours on the hour are `21 h` (`common_time_on_the_hour`); the weekly reset keeps
  `00:00 UTC`. Durations: `j` (jours), `h`, `min`. Compact numbers: `5 k`, `1,5 M`.
- Multipliers: `×2` stays in short bonus lines (*Ce poisson ×2.*); in sentences French says
  *3 fois plus* rather than *3×*.

## Game terms

| English | French | Notes |
| --- | --- | --- |
| Deep Line, Dex | Deep Line, Dex | Never translated; *le Dex* |
| Fish (tab) / Dex / Shop / Battle | Pêche / Dex / Boutique / Duel | Bottom tabs |
| Species; Seen; Caught | Espèces; Vu; Pêché | Counts of species agree in the feminine (*Vues 12 · Pêchées 34 / 200*); a single fish's page uses the masculine (*Vu, pas encore pêché*) |
| Cast (noun, verb); Cast line | lancer (m.), lancer; Lancer la ligne | |
| Hook (noun / verb) | hameçon / ferrer | Objects are *accrochés* (snag = accrocher) |
| Line | ligne | Also the skin slot *Ligne* |
| Rope; Rope Length, Strength, Agility | corde; Longueur de corde, Solidité de corde, Agilité de corde | Kept distinct from *ligne*. Short forms: Longueur, Solidité, Agilité |
| Bump; bump-free | choc; sans choc | *Zone sans choc*, *Les 20 premiers mètres sont sans choc* |
| Head | tête | |
| Load (weight bar) | Charge | |
| Reel in fast | Remontée rapide | The button; *Ligne pleine · Remontée rapide* |
| Tackle | Matériel | Shop page and one-cast items |
| Lantern | Lanterne | Capitalised, as a name |
| Rare fish detector, Lucky charm, Sparkle lure | Détecteur de poissons rares, Porte-bonheur, Leurre scintillant | Short: Détecteur. *Porte-bonheur* is invariable |
| Sparkling | scintillant | Reserved for golden fish: never use *scintiller* for anything else |
| Sunlit Shallows / Twilight Reef / Midnight Trench / Abyssal Floor | Hauts-fonds ensoleillés / Récif crépusculaire / Fosse de minuit / Plaine abyssale | Short: Hauts-fonds, Récif, Fosse, Plaine |
| Points, pts | points, pts | |
| Milestones | Paliers | As in the listing |
| Fishing secrets, Secrets | Secrets de pêche, Secrets | Secret titles (`core_secret_*`) never appear outside the secrets' own texts |
| Skins | Apparences | Dex section; "looks" in prose is *look(s)* |
| Title; title badge; Dex cards | Titre; badge de titre; cartes du Dex | |
| Atmosphere: Family, Nightmare | Ambiance : Famille, Cauchemar | |
| Themes; Day, Night, Like my phone | Thèmes; Jour, Nuit, Comme mon téléphone | |
| Shop pages Rope, Tackle, Style, Passes, Aquarium | Corde, Matériel, Style, Pass, Aquarium | *pass* is invariable |
| Captain's Club | Club du Capitaine | |
| Season pass, Collector's edition, Weekend event, Starter pack, Founder's pack | Pass saison, Édition collector, Événement du week-end, Pack de démarrage, Pack du fondateur | |
| Season tier | niveau | Rope level is *Niv.* (short) |
| Best of 5, 3-Minute Rush, Heavy Haul, Species Hunt, Deep Dive | Au meilleur des 5, Course de 3 minutes, Plus grosse pêche, Chasse aux espèces, Plongée profonde | As in the listing |
| Easy, Normal, Hard; Rival | Facile, Normal, Difficile; Rival | Challenges: Facile, Moyen, Difficile |
| Round; forfeit; rematch | manche; abandon / abandonner; revanche | |
| Quick match, room, room code | Partie rapide, salon, code de salon | The code itself never |
| Wager, stake | pari, mise | |
| Gift, cloud save, recovery code | cadeau, sauvegarde cloud, code de récupération | |
| Player id, friend code | identifiant de joueur, code ami | |
| Daily reward, streak, streak shield | récompense du jour, série, bouclier de série | |
| Daily challenges, swap | défis du jour, changer / changement | |
| Migration | migration | |
| Friends and rankings, leaderboards | Amis et classements, classements | |
| Best cast, Deepest catch, Rarest catch; Everyone, League, Friends | Meilleur lancer, Prise la plus profonde, Prise la plus rare; Tous, Ligue, Amis | |
| Weekly tournament, attempt | tournoi hebdomadaire, tentative | |
| Club, owner, member, club goal | club, chef, membre, objectif du club | Club names stay as generated |
| Angler | pêcheur | |
| Fish log | carnet de pêche | |
| Claim; Collect (after a cast) | Récupérer; Encaisser | |
| Settings; the phone's settings | Paramètres; réglages du téléphone | |
| Tour | visite | *Faire la visite*, *Passer la visite* |

## Names inside sentences

French articles, elision (*de* / *d'*) and agreement change around a name, so names that come
from data (players, species, items, options, zones, modes, clubs) are placed where nothing
touches them:

- after a colon: *Trop lourd : %1$s*, *Option verrouillée : %1$s*, *À suivre : %1$s*,
  *%1$s : lancez !*;
- in parentheses: *En attente de votre adversaire (%1$s)…*;
- in « »: *Acheter « %1$s » ?*, *Débloquer « %1$s » ?*;
- after a head noun: *dans la zone %1$s*, *le club %1$s*, *le mode %1$s*, *le titre %1$s*;
- after *à* or *par*, which never elide: *Passez le téléphone à %1$s.*,
  *Cadeau envoyé par %1$s.* (so `shop_gift_from_you` is *vous-même*).
- Never *de %1$s* or *le %1$s* before a name (*de Ana* / *le Abyss Wyrm*).
- Gendered participles about a player are avoided (*%1$s fait maintenant partie de vos amis*,
  *Il ne reste que vous*).
- Fixed names may be written into a sentence with their article, as the tour does
  (*Atteignez la Plaine abyssale pour débloquer Cauchemar*).

## Plurals

French has `one` (0 and 1), `many` (exact millions) and `other`. `many` repeats `other`, except
where a count of points, Lanterns, species, fish or anglers is directly followed by its noun: there
it adds *de* (*1 000 000 de points*). `one` always keeps the number (`%d`), since it also covers 0.
