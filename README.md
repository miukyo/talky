# Talky

**Talky** is a modern, Folia-ready cinematic NPC dialogue plugin built specifically for **Paper 26.2** running on **JDK 24** (or newer).

It transforms NPC interactions into immersive cinematic dialogues featuring smooth camera rotation, camera locking, FOV zooming, blindness atmosphere, multiline chat styling with MiniMessage/PAPI, per-line command execution, and flexible progression modes (timer, click, sneak).

---

## Features

- **Folia & Paper Native**: Built using unified region-safe scheduling (`RegionScheduler`, `AsyncScheduler`, `GlobalRegionScheduler`) with fallback to standard Paper Bukkit scheduler.
- **Cinematic Camera Lock & Smooth Rotation**:
  - Smoothly turns player camera to face NPC eye level using ease-in-out smoothstep curves over configurable ticks.
  - Locks player camera yaw and pitch onto NPC during conversation while allowing free body movement.
  - Anti-fly safe: jumping or falling players are not frozen mid-air; gravity lands players safely before anchoring.
  - Initiation grace period allows running or holding `W` towards NPC without accidental cancel.
- **Atmospheric Effects**:
  - **FOV Zoom**: Applies slowness effect for camera zoom without jarring double-FOV FOV jumps.
  - **Blindness**: Cinematic fog that focuses player attention on the speaking NPC.
- **Rich Text Formatting**:
  - Full **MiniMessage** support (`<color>`, `<gradient>`, `<bold>`, `<click>`, `<hover>`).
  - Supports multiline dialogue as YAML string lists, including blank lines for spacing.
  - Legacy color code compatibility (`&a`, `§a`) and hex (`&#RRGGBB`).
  - **PlaceholderAPI** integration with player (`%player%`, `%player_name%`) and NPC (`%npc%`) replacements.
  - Oraxen glyphs and font shifts supported.
- **Action Triggers & Commands**:
  - Run console commands or player commands on each dialogue line.
  - Run completion commands and sounds when dialogue concludes.
- **Progression Modes**:
  - `AUTO`: Timed progression using tick delays.
  - `MANUAL`: Dialogue pauses on each line until player sneaks or clicks.
  - `HYBRID`: Automatically advances after delay, or advances immediately if player clicks/sneaks to skip ahead.
- **Broad NPC Support**:
  - FancyNpcs (`NpcInteractEvent`)
  - Citizens (`NPCRightClickEvent`)
  - Vanilla named entities, villagers, and interaction entities (`PlayerInteractEntityEvent`)
  - Direct command trigger (`/talk <id>`)

---

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/talk <talk_id>` | `talky.use` | Start conversation directly on self |
| `/talky start <talk_id> [player]` | `talky.admin` | Force-start conversation for self or target player |
| `/talky stop [player]` | `talky.admin` | Stop active conversation for self or target player |
| `/talky next` | `talky.use` | Manually advance current active conversation |
| `/talky list` | `talky.admin` | List all loaded dialogue configurations and NPC bindings |
| `/talky reload` | `talky.admin` | Reload all YAML configs and talk files |

Default permissions:
- `talky.use`: Given to all players by default (`default: true`).
- `talky.admin`: Given to OP by default (`default: op`).

---

## Global Configuration (`config.yml`)

Located at `plugins/Talky/config.yml`. Values here act as global defaults for any dialogue file that does not override them.

```yaml
# =========================================================
# Talky Global Plugin Configuration
# =========================================================

settings:
  # Default line delay in ticks if not specified in talk file (20 ticks = 1 second)
  default-line-delay: 45

  # FOV Zoom settings
  fov:
    # Enables camera zoom when conversation starts
    enabled: true
    # Amplifier for Slowness effect (0 = slight zoom, 1 = noticeable zoom, 2 = dramatic zoom)
    amplifier: 2

  # Blindness effect settings (cinematic dark fog focusing player on NPC)
  blindness:
    enabled: true

  # Movement cancellation settings
  movement:
    # Whether moving away cancels active talk
    cancel-on-move: true
    # Distance in blocks before dialogue cancels (measured after settling)
    move-threshold: 0.8
    # Grace period in ticks on talk start (allows running towards NPC / holding W without instant cancel)
    initiation-grace-ticks: 25
    # Message sent to player when walking away cancels dialogue (supports MiniMessage)
    cancel-message: "<red><i>You walked away and ended the conversation.</i></red>"
    # Sound played on movement cancel (sound key, leave empty to disable)
    cancel-sound: "block.fire.extinguish"

  # Interaction & Camera settings
  interaction:
    # Lock camera yaw & pitch to NPC eye level (body can still move)
    lock-camera: true
    # Number of ticks for smoothstep initial camera turn towards NPC
    camera-smooth-ticks: 12

  # Whether taking damage cancels active talk
  cancel-on-damage: true

  # Dialogue advancement settings
  advance:
    # Mode:
    #   HYBRID = auto-advances on delay, player can click/sneak to skip ahead
    #   MANUAL = strictly waits for player sneak/click on every line
    #   AUTO   = strictly timed, ignores player sneak/click
    mode: HYBRID
    # Advance to next line when pressing sneak (Shift)
    advance-on-sneak: true
    # Advance to next line when clicking (left/right click)
    advance-on-click: true

# Chat formatting
chat:
  # Optional header sent to player when talk starts (leave empty for none)
  header: ""
  # Optional footer sent to player when talk concludes (leave empty for none)
  footer: ""
  # Separator line format
  separator: "<dark_gray>----------------------------------------</dark_gray>"

# System messages
messages:
  prefix: "<gold><bold>Talky</bold></gold> <dark_gray>»</dark_gray> "
  already-talking: "<red>You are already in a conversation! Finish or walk away to end it.</red>"
  talk-not-found: "<red>Conversation '%talk%' not found.</red>"
  cooldown: "<gray>The NPC is busy. Please wait <gold>%time%s</gold>.</gray>"
  no-permission: "<red>You do not have permission to talk to this NPC.</red>"
  reloaded: "<green>Talky configuration and dialogues reloaded successfully!</green>"
  player-only: "<red>This command can only be run by a player.</red>"
  stopped: "<yellow>Conversation has been stopped.</yellow>"
```

---

## Dialogue File Specification (`talks/*.yml`)

Each conversation is stored as its own YAML file inside `plugins/Talky/talks/<talk_id>.yml`.

### Full Option Reference

```yaml
# Unique identifier matching file name (without .yml)
id: quest_guide

# Title displayed in admin commands and logs
title: "Quest Guide"

# NPC or entity display names that trigger this conversation on right-click
# Matches Citizens NPC name, FancyNpcs name, or named vanilla entity / villager
npc-names:
  - "Guide"
  - "Quest Master"
  - "s_guide_npc"

# Cooldown in seconds per player before this talk can be triggered again (0 to disable)
cooldown: 10

# Optional permission required to talk to this NPC (empty = anyone can talk)
permission: "willowdale.talk.guide"

# FOV Zoom override (slowness potion effect)
fov:
  enabled: true
  amplifier: 2

# Blindness effect override (cinematic fog)
blindness:
  enabled: true

# Camera lock override
interaction:
  # Lock player camera to NPC eye level
  lock-camera: true
  # Ticks spent smoothly panning player camera to NPC on start
  camera-smooth-ticks: 12

# Movement cancellation override
movement:
  # If true, walking further than move-threshold blocks cancels dialogue
  cancel-on-move: true
  # Max distance in blocks player can walk from anchored point
  move-threshold: 0.8
  # Ticks after right-click before movement checks start (prevents running cancels)
  initiation-grace-ticks: 25
  # Message sent if player walks away
  cancel-message: "<red><i>You walked away before the guide finished speaking.</i></red>"
  # Sound played on cancel
  cancel-sound: "block.fire.extinguish"

# Advancement mode override
advance:
  # Progression mode: AUTO, MANUAL, or HYBRID
  mode: HYBRID
  # Player can sneak (Shift) to advance to next line
  advance-on-sneak: true
  # Player can left/right click to advance to next line
  advance-on-click: true

# Dialogue lines (delivered sequentially)
lines:
  # Format 1: Full block with multiline YAML list, custom delay, sound, and commands
  - text:
      - ""
      - "%oraxen_allay_wave%<white>Welcome to Willowdale, <yellow>%player%</yellow>!"
      - "<gray>I have important news regarding the royal treasury.</gray>"
      - ""
    delay: 45
    sound: "entity.villager.ambient"
    commands:
      - "[console] effect give %player% speed 5 0 true"
      - "[player] me nods attentively"

  # Format 2: Single string text with manual pause (waits for click/sneak regardless of mode)
  - text: "<white>Press <key:key.sneak> or click anywhere when you are ready to continue.</white>"
    manual: true
    sound: "entity.villager.ambient"

  # Format 3: Single string text with custom delay in ticks (20 ticks = 1 second)
  - text: "<white>Take this introductory starter pack to aid your journey.</white>"
    delay: 40
    sound: "entity.villager.yes"
    command: "give %player% bread 3"

  # Format 4: Short string format (uses default delay and no sound/command)
  - "<gray><i>The guide marks your map with coordinates.</i></gray>"

# Actions triggered when dialogue completes all lines
on-complete:
  # Sound played on finish
  sound: "ui.toast.challenge_complete"
  # Message sent to player on finish (empty for none)
  message: "<green>Dialogue completed! Safe travels, %player%.</green>"
  # Commands executed on completion
  commands:
    - "[console] experience add %player% 10 points"
    - "[console] title %player% subtitle {\"text\":\"Quest Accepted!\",\"color\":\"gold\"}"
```

---

## Line Properties Deep-Dive

Each entry under `lines:` can use these properties:

| Property | Type | Description |
|---|---|---|
| `text` | `String` or `List<String>` | Dialogue message. Can be a single string or YAML list. Blank strings `""` print blank lines in chat. Supports MiniMessage, legacy `&`/`§`, Hex, and PAPI. |
| `delay` | `Integer` | Duration in ticks before auto-advancing (in `AUTO` or `HYBRID` mode). If `-1`, pauses manually. |
| `manual` | `Boolean` | If `true`, pauses dialogue indefinitely until the player clicks or sneaks. Equivalent to `delay: -1`. |
| `sound` | `String` | Sound played to player when line is displayed (e.g. `entity.villager.ambient`, `entity.experience_orb.pickup`). |
| `commands` / `command` | `List<String>` or `String` | Commands executed when line is delivered. |

### Command Execution Prefixes
- `[console] <command>`: Executes command from console (default if no prefix given).
- `[player] <command>`: Forces the interacting player to run command (`player.performCommand`).
- Placeholders:
  - `%player%`: Interacting player username.
  - `%npc%`: Interacting NPC display name.

---

## Dialogue Advancement Modes

1. **`HYBRID` (Recommended)**:
   - Dialogue progresses automatically based on each line's `delay`.
   - If player clicks or sneaks, dialogue skips timer and immediately displays the next line.
   - Ideal for player-friendly dialogues where fast readers can click through.

2. **`MANUAL`**:
   - Dialogue always pauses on each line.
   - Advances only when player clicks, sneaks, or runs `/talky next`.
   - Ideal for important story cutscenes or tutorials where reading comprehension is required.

3. **`AUTO`**:
   - Dialogue progresses strictly based on line delays.
   - Clicking and sneaking are disabled.
   - Ideal for cinematic announcements or background radio chatter.

---

## Requirements & Building from Source

- **Server**: Paper 26.2 build
- **Java**: JDK 24 or newer (`maven.compiler.release`: 24)
- **Build Tool**: Maven 3.8+

```bash
mvn clean package
```

The compiled JAR will output to `target/talky-1.0.0.jar`.
