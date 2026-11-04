---
name: new-remote-action
description: Add a feature action to the Dice Familiar remote-action layer so it can run locally and over LAN (e.g. the planned cards.draw). Use when a feature needs a RemoteAction/RemoteActionHandler, or when extending LAN play beyond dice.
---

# New remote action

The remote-action layer (`domain/remote/`) and LAN (`domain/lan/`, `data/lan/`) are **feature-agnostic**. A feature plugs in through an action and a handler; never add feature logic to the registry, codec, session manager or transport.

## Steps

1. **Read the existing pattern:** `domain/remote/RemoteAction.kt`, `RemoteActionRegistry.kt`, `ActionEnvelopes.kt`, `LocalActionExecutor.kt`, and the dice example (`DiceRollAction`, `DiceRollHandler`, `di/DiceModule.kt`, `di/RemoteModule.kt`).
2. **Define the action** in `domain/<feature>/`: a `RemoteAction<Req, Res>` with `type` (`<feature>.<verb>`, e.g. `cards.draw`), `version = 1` and kotlinx.serialization serializers. Requests are untrusted input: validate them in the handler and return a rejection instead of throwing.
3. **Handler:** a `RemoteActionHandler` that does the work on the receiving device. **That device owns the randomness** (its own `RandomSource`). Unit-test it with a seeded source, including malformed and out-of-range requests (`domain-tdd` can do this part).
4. **Register with Hilt:** `@Provides @IntoSet fun ...(): RemoteActionHandler<*, *>` in the feature's module.
5. **Local path:** the feature's ViewModel calls the action through `LocalActionExecutor`, the same path a LAN request takes.
6. **Compatibility:** changing an existing action's payload must stay backward compatible (optional fields with defaults, as with `dice.roll`'s `pool` flag) or bump its version.
7. **LAN UI / gateway:** follow the dice pattern (`LanDiceGateway`, host request mode, player banner) with a feature gateway that has a no-LAN fake for tests.
8. **Desktop stand-in:** if the action is used over LAN, delegate to `lan-protocol-keeper` to mirror it in `tools/lan_desktop/dicefamiliar_lan.py`, regenerate `app/src/test/resources/lan/` and run the LAN desktop tests.
9. **Docs:** update the CLAUDE.md *Shared → Remote actions / LAN play* and the feature section, and README's *LAN play*.
