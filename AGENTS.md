## Filling empty diff mappings

- Work in `mappings/diff/mapping-<from>to<to>.json`.
- Source keys come from `mappings/mapping-<from>.json`.
- Target values come from `mappings/mapping-<to>.json`.
- Empty mappings are `""`; fill only entries you can map confidently.
- Mapping files are VERY large, only read the sections/arrays for the given key.

## Blockstates

- `blockstates` keys are source blockstates, usually full strings:
  `minecraft:block[prop=value,...]`.
- Values are target blockstates with full properties:
  `"old_block[prop=a]": "new_block[prop=b]"`.
- If no exact source-state key is found, the optimizer also checks the bare block key:
  `"old_block": "new_block[prop=b]"`.
- A value ending with `[` copies the original properties:
  `"old_block": "new_block["`.
- Use `""` only when the source intentionally has no target mapping.

## Other sections

- Sections like `items`, `entities`, `sounds`, and names map source identifier/name keys to target values.
- Keep existing section names and JSON object structure unchanged.
- Do not invent target identifiers; verify them in the target `mapping-<to>.json`.
