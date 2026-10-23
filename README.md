# QuickShop

Quick Buy shop plugin for Paper 1.21.5+ (needs Vault + an economy plugin).

- `/shop` opens the Quick Buy chest. Click an empty slot to add items via dialog screens.
- Left click = buy (no cooldown), right click = remove.
- `/shards` shows shards (10 per kill). Spawners run `givespawner <player> <mob> <amount>`.
- Prices are in `prices.yml` (`/shop reload` to apply changes).

## Build
Push to GitHub, open the **Actions** tab, open the latest run, and download the `QuickShop` artifact (the jar).
Or locally: `mvn package` -> `target/QuickShop-1.0.0.jar`.

## Shards commands
Everyone:
- `/shards` - your balance
- `/shards <player>` - check another player's balance
- `/shards pay <player> <amount>` - send some of your shards to a player

Admins only (permission `quickshop.shards.admin`, default op):
- `/shards give <player> <amount>`
- `/shards take <player> <amount>`
- `/shards set <player> <amount>`
