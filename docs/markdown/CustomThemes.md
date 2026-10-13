# Custom Themes

A custom theme is a JSON document in `~/.forge/themes` that starts from one of Forge's
built-in themes and changes what you want changed: Swing UI defaults for the window, token
colours for the editor, or both. Every `.json` file in that folder is one theme, and it
appears in **Settings → Theme** next to the built-in themes, marked "(custom)".

## Quick start

1. Open **Settings → Theme** and choose the theme you want to start from.
2. Click **New Theme…** and give it a name. Forge writes a document that copies the
   current theme, switches to it, and opens the document in the editor.
3. Change a colour and save. The theme is re-applied the moment the file is saved.

If you edit a document in another program, click **Reload** on the Theme page instead.

## The document

```json
{
    "schemaVersion": 1,
    "name": "Copper Night",
    "base": "tokyo-night",
    "swing": {
        "@accentColor": "#D99A70",
        "TextPane.background": "#16161E",
        "Component.arc": 12
    },
    "tokens": {
        "keyword": { "color": "#D99A70", "bold": true },
        "string": { "color": "#9ECE6A" },
        "comment": { "bold": false }
    }
}
```

Every property is optional. **Anything left out comes from the base theme**, so a document
only has to say what it changes. `{}` on its own is a valid theme: a copy of Material Darker.

| Property        | Meaning                                                                                       |
|-----------------|-----------------------------------------------------------------------------------------------|
| `schemaVersion` | The document format. Currently `1`, and `1` when left out.                                    |
| `name`          | Shown in the theme list and status bar. Defaults to the file name.                            |
| `base`          | The built-in theme to start from (see below). Defaults to `material-darker`.                  |
| `swing`         | FlatLaf UI defaults to change, written exactly as in a FlatLaf `.properties` file.             |
| `tokens`        | Token types to restyle, each with an optional `color` (`#RRGGBB` or `#RGB`) and `bold`.        |

**Base themes:** `material-darker`, `forge-dark`, `forge-light`, `tokyo-night`,
`catppuccin-mocha`, `swing-metal`, `swing-system`. A custom theme cannot be the base of
another one.

**Token types:** `plain`, `keyword`, `literal`, `type`, `type-declaration`,
`method-declaration`, `method-call`, `string`, `character`, `number`, `comment`,
`doc-comment`, `todo`, `annotation`, `parentheses`, `brackets`, `braces`, `operator`,
`punctuation`. Case and `-`/`_` do not matter, and `"colour"` works as well as `"color"`.
There is no italic, on purpose: the bundled editor font is already an italic cut.

### The `swing` section

These are the same keys and values FlatLaf reads from its own theme files, so everything
FlatLaf understands works here:

- plain values: `"#D99A70"`, `12`, `true`
- variables, which every key built from them follows: `"@accentColor"`, `"@background"`,
  `"@foreground"`, `"@selectionBackground"`
- references to other keys: `"$TextPane.background"`
- colour functions: `"darken(@background, 5%)"`, `"lighten(@accentColor, 10%)"`

To find a key, click **Browse UI Keys…**. It lists every UI default of the look and feel
on screen, with its current value.

The editor reads four of these keys itself, so they are the ones that have to agree with
your token colours:

| Key                           | Used for                                                       |
|-------------------------------|----------------------------------------------------------------|
| `TextPane.background`         | The editor background; the gutter and current-line band are mixed from it. |
| `TextPane.foreground`         | Mixed with the background for the gutter and fold markers.     |
| `TextPane.selectionBackground`| The selection, drawn translucently under the token colours.    |
| `Component.accentColor`       | The caret, bracket highlight, and active line number.          |

Things worth knowing:

- Only FlatLaf themes read the `swing` section. With `swing-metal` or `swing-system` as the
  base it is ignored, and Forge says so.
- `forge-dark`, `forge-light`, `tokyo-night` and `catppuccin-mocha` are built from FlatLaf's
  variables, so changing `@background` or `@accentColor` there recolours everything built
  from them. `material-darker` comes from an IntelliJ theme file that sets many keys
  directly, so a variable may not reach every key. Set the key itself when it doesn't.

## When something is wrong

Forge never throws a whole theme away over one typo:

- A **misspelt token type** or a **colour that isn't a colour** skips only that entry.
- **Invalid JSON**, an **unknown property**, or an **unknown base** stops that one document
  from loading, with the line and column of the mistake.
- If the document of the theme you are using breaks while you edit it, the last good
  version stays on screen until it is fixed.
- If the theme saved in `settings.json` cannot be found at startup, Material Darker is used
  for that session, but the saved choice is kept, so the theme comes back once its document
  loads again.

Problems are printed to the Console (prefixed `Theme:`) and listed on the Theme settings
page under **Problems in theme documents**.

## How it works

```text
~/.forge/themes/*.json
    -> CustomThemeDocument   the file's shape (kotlinx.serialization)
    -> CustomTheme           checked values: base + Swing overrides + restyled TokenTheme
    -> ThemeCatalog          built-in AppThemes followed by the CustomThemes
    -> ThemeService          installs a Theme, saves its id, reloads on save
```

- `Theme` is a sealed interface with exactly two kinds. `AppTheme` is an enum because
  the built-in themes are known when Forge is compiled; `CustomTheme` is a class because
  custom themes only exist once their files have been read.
- A custom theme's id is `custom:` plus its file name, for example
  `custom:copper-night`. That is what `settings.json` stores.
- The `swing` section is handed to FlatLaf with `FlatLaf.setGlobalExtraDefaults(...)`.
  FlatLaf merges these into the base theme's own properties *before* it works out any value,
  which is why overriding a variable reaches every key built from it. They are replaced on
  every theme change (with nothing for a built-in theme), so one custom theme's changes never
  leak into the next.
- Token colours start from the base theme's `TokenTheme`, and `TokenTheme.withStyles(...)`
  replaces only the token types the document lists.
- Theme documents are read at startup as a bootstrap phase, off the Event Dispatch Thread,
  and again in a `SwingWorker` whenever one is saved in the editor or **Reload** is clicked.
