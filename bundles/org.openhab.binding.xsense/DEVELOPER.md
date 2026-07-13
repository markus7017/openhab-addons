# Developer Guide

This document helps you get productive on the X-Sense binding and submit a good pull request.
It is guidance, not a full code reference — read the code and `README.md` for details.

## Architecture in One Minute

```text
Bridge xsense:account   (email, password)        — cloud session, owns the API client
 └─ Bridge xsense:home     (houseId)              — a home/house of the account
     └─ Bridge xsense:station  (stationSn)        — SBS50 base station
         └─ Thing xsense:smoke | co | smokeco | heat | water | thermohygro
                  | listener | driveway | mailbox | door | motion | strobe | keypad  (deviceSn)
```

- `internal/api` — cloud client (`XSenseApiClient`), Cognito SRP auth (`XSenseCognitoSrp`), pure-Java
  AWS SigV4 signer (`XSenseAwsSigV4`), device-shadow request builder (`XSenseShadowRequests`),
  DTOs.
- `internal/config` — thing/binding configuration classes.
- `internal/handler` — one handler per thing level; `XSensePath` (hierarchy path/unique id) and
  `XSenseChannelLabeler` (dynamic channel labels) are shared helpers.
- `internal/discovery` — cloud discovery service.
- `internal/manager` — the XSense Manager web view.

Data flows one way, top to bottom: the account handler polls the cloud, builds an inventory
snapshot, and calls `updateFromInventory(...)` on each home handler, which does the same for its
stations, which do the same for their sensors. Every handler's `updateFromInventory` is the single
place that applies inventory data to that thing — the planned live-update (MQTT/WSS) phase is
expected to reuse the same entry points rather than adding a parallel path.

## Cloud Connection Design

X-Sense devices have no documented local API (checked manuals, HA community,
GitHub); all communication goes through the X-Sense cloud, which itself is a
thin layer over AWS services (Cognito for identity, a REST gateway for
inventory/control, AWS IoT Core for live state). The binding therefore has to
speak three distinct protocols to reach a device, in this order:

1. **Cognito SRP** — turns an email/password into an AWS access/id/refresh
   token.
1. **REST (`api.x-sense-iot.com`)** — inventory polling and, since the
   security/control phase, arm/disarm and mute commands via AWS IoT device
   shadows exposed through the same REST gateway.
1. **AWS IoT MQTT over WSS** — planned; live alarm/state push instead of
   polling. Not implemented yet, see below.

### Authentication (AWS Cognito, SRP)

Login uses an AWS Cognito user pool with the `USER_SRP_AUTH` flow, not a
plain username/password POST:

1. The Cognito client ID and user pool ID aren't fixed — they're fetched from
   the X-Sense REST gateway itself via an unauthenticated call (`bizCode`
   `101001`; separate app registrations exist for iOS and Android, the
   binding uses the iOS one).
1. `XSenseCognitoSrp` runs the two-step SRP exchange against
   `https://cognito-idp.us-east-1.amazonaws.com`: send username + the SRP
   public value `A`, receive a salt/verifier/secret block from the server,
   compute the password claim (`BigInteger` modular arithmetic +
   HMAC-SHA256), and receive back `access_token`, `id_token`,
   `refresh_token`.
1. This is implemented in pure Java (`BigInteger`, `MessageDigest`, `Mac`) —
   no AWS SDK dependency, following the same math as the well-known
   `warrant`/`pycognito` Python SRP implementations. Keeping this
   dependency-free was a deliberate call to keep the bundle lean; the SDK
   would pull in a large transitive dependency tree for a login flow that's
   ~300 lines of hand-rolled crypto.
1. The access token is short-lived (~1 h). Refresh uses `REFRESH_TOKEN_AUTH`
   with a `SECRET_HASH = Base64(HMAC-SHA256(secret, username + clientId))`,
   where `username` is the `USER_ID_FOR_SRP` value captured at login time —
   not the email address and not the client secret itself, a detail that's
   easy to get wrong. If refresh fails, the handler falls back to a full SRP
   re-login rather than treating it as a fatal error.
1. REST calls after login authenticate with header
   `Authorization: <access_token>`.

### REST API

- Single endpoint for everything except Cognito:
  `POST https://api.x-sense-iot.com/app`. There is no per-resource URL
  structure — every request is a JSON body whose `bizCode` field selects the
  operation (inventory query, history, AWS credential vending, and so on).
- Every request body also carries `clientType`, `appVersion`, `appCode`, and
  a `mac` field — an MD5 integrity hash computed over the values of the
  custom request fields plus a shared secret. This is _not_ a network MAC
  address; the name is misleading and comes straight from the vendor API.
  The hash is order-sensitive, so `XSenseApiClient` builds request fields in
  the same order the reference implementations use rather than relying on
  map iteration order.
- Inventory is enumerated top-down: houses (`bizCode 102007`, which also
  returns `mqttRegion` and `mqttServer` per house — currently unused,
  reserved for the MQTT phase) → stations and their attached devices per
  house.

### Device Shadows and AWS SigV4 (arm/disarm, mute)

Arming/disarming a station and muting an alarm are not separate REST
commands — they are writes to an **AWS IoT device shadow**, delivered over
the same REST gateway rather than over MQTT:

```text
POST https://{mqttRegion}.x-sense-iot.com/things/SBS50{stationSn}/shadow?name=2nd_appmode
```

This request has to be signed with **AWS Signature Version 4**, using
temporary AWS credentials (`accessKeyId`/`secretAccessKey`/`sessionToken`)
vended by the X-Sense REST gateway itself (`bizCode 101003`, authenticated
with the Cognito access token). `XSenseAwsSigV4` is a pure-Java SigV4 signer
(canonical request → string to sign → HMAC-SHA256 signing key chain →
`Authorization` header), verified against the official AWS test-suite
vectors (`get-vanilla`, `post-vanilla`, `query-order`) so its output is
byte-for-byte what the AWS SDK would produce, without depending on the SDK.
`XSenseShadowRequests` builds the desired-state JSON body per operation
(station `safeMode`, device `mute`) that gets signed and sent.

On a 401/403 the client refreshes the AWS credentials (bizCode 101003) once
and retries; a persistent 403 after that usually means the IAM policy
attached to those temporary credentials is MQTT-only and doesn't cover this
REST shadow path for the account/region in question — that case is logged
and left for the user rather than retried indefinitely. There's no
optimistic channel update after sending a command: the shadow write has no
synchronous readback in this API, so the channel reconciles on the next
inventory poll instead of assuming success.

### Live State: MQTT over WSS (planned, not yet implemented)

The REST API above is pull-only: alarm and sensor-value changes only become
visible on the next poll (default 300 s). The X-Sense app instead receives
live updates by subscribing to the same device shadows over **MQTT via a
SigV4-presigned WebSocket** to AWS IoT Core (`wss://{mqttServer}/mqtt`),
using the same temporary AWS credentials as the shadow REST calls. Relevant
topics (see `protocol.md` in the `oh-xsense` skill for the full list):
shadow update topics per station/house, and
`$aws/events/presence/+/{thingName}` for station online/offline. This is the
next roadmap phase — the binding is not currently subscribed to any MQTT
topic, and `updateFromInventory()` on every handler is deliberately the
single place inventory data is applied so that a future MQTT listener can
call the same entry points instead of adding a second, divergent update
path.

**Why this can't use openHAB's own MQTT transport:**
`org.openhab.core.io.transport.mqtt`'s `MqttBrokerConnection` builds its
WebSocket connection via the HiveMQ client's `webSocketWithDefaultConfig()`,
which hardcodes the WebSocket path to `/mqtt` and has no way to attach a
query string. AWS IoT Core's SigV4-presigned URLs, however, _are_ a query
string — the signature, credential scope, and session token are all passed
as `X-Amz-*` query parameters on the connection URL. Without query-string
support there is no way to present a valid signed URL to AWS IoT Core
through core's transport, so the binding will need to open its own MQTT
client rather than registering a `MqttBrokerConnection` with the framework.
Two approaches, both with precedent elsewhere in `openhab-addons`:

1. Embed the HiveMQ MQTT client directly
   (`com.hivemq:hivemq-mqtt-client`) instead of going through openHAB core's
   wrapper — its `MqttWebSocketConfig` (≥1.2) supports both `serverPath` and
   `queryString`, so the presigned URL can be used as-is.
1. Use the AWS IoT Device SDK
   (`software.amazon.awssdk.iotdevicesdk:aws-iot-device-sdk` + `aws-crt`),
   which handles the SigV4 WebSocket handshake internally. The
   `worxlandroid` binding already carries this dependency for the same
   reason (AWS IoT-backed device).

The `salus` binding is the precedent for the SigV4-signing half specifically
(`uk.co.lucasweb:aws-v4-signer-java`), though this binding's `XSenseAwsSigV4`
already covers that piece without an extra dependency — the open question
for the MQTT phase is purely the WebSocket transport, not the signing.

## Adding a New Sensor Model

Most new models are just a mapping addition:

1. Add the model code to `DEVICE_TYPE_TO_THING_TYPE` in `XSenseBindingConstants` if it fits an
   existing thing type (smoke/co/smokeco/heat/water/thermohygro or one of the security types
   listener/driveway/mailbox/door/motion/strobe/keypad).
1. If it needs a genuinely new thing type, add a `<thing-type>` in `thing-types.xml` (reuse the
   existing `<channel-group>` types where the channels match), a config class if it needs specific
   parameters, and register it in `XSenseHandlerFactory` and `XSenseBindingConstants`.
1. Regenerate i18n: `mvn i18n:generate-default-translations`, then add any thing-status keys the
   handler references (see the existing `offline.*` keys).
1. Add a DTO test with a real (or representative) JSON payload for the model.

## Adding a New Channel

1. Add the `<channel-type>` (and a `<channel-group-type>` if it's a new logical group) to
   `thing-types.xml`. Reuse an existing group across thing types where the semantics match — don't
   duplicate a group definition per thing type.
1. Regenerate i18n and check the label/description look right in the generated `.properties`.
1. Publish the value from the owning handler via its `updateChannel()` method, backed by the shared
   `XSenseChannelState` dedup cache — never call `updateState()` directly (see any handler for the
   pattern).
1. If the channel needs a stable semantic tag, verify it exists in the openHAB 5.x vocabulary
   before using it (`grep` the `org.openhab.core.semantics` jar, see `oh-generic` skill notes) —
   several plausible-looking tags (`CODetector`, `RFPower`) don't exist.

## Adding Discovery for a New Level

Follow the existing progressive pattern in `XSenseCloudDiscoveryService`: a level is only published
once its parent thing already exists in the registry, and every poll re-publishes all levels whose
parent exists. Don't skip this — flat "publish everything at once" discovery breaks the bridge
approval flow.

## Manual Configuration

Every thing can also be created via a `.things` file — the handlers only depend on
`getConfigAs()`, not on discovery. Keep this working: don't introduce state that only gets
initialized from a discovery result. See the README's `.things` example.

## Testing Conventions

Standard openHAB rules apply (see the `oh-generic` skill for full detail): no Javadoc in test
classes, camelCase test method names, no raw `RuntimeException`, `assertNotNull` after
`gson.fromJson()`. Prefer small, focused test classes per production class over one giant test
class.

## Code Quality and Long-Term Maintainability

- Keep classes under roughly 1000 lines; split by responsibility (e.g. this binding separates
  `api`, `config`, `handler`, `discovery`, `manager`) rather than growing one file.
- When fixing a bug or adding a feature, it's fine — and encouraged — to clean up the directly
  related code you're already touching (extract a confusing block, fix a stale comment, tighten a
  null check). Don't turn the change into an unrelated rewrite of code you didn't otherwise need to
  touch; that hides the actual fix in review and risks regressions in code nobody asked to change.
  This is how the codebase improves over time without ever needing a big-bang refactor.
- Thread safety matters where handlers genuinely share state across threads (scheduler callbacks,
  discovery, dispose vs. in-flight work) — see the account handler's `disposed` flag and volatile
  fields for the pattern. Don't add `synchronized`/`volatile` defensively where nothing is actually
  shared; it adds cost and noise without a benefit.
- See the `oh-generic` skill's binding lifecycle guide for the full policy this project follows
  across binding creation, review, and maintenance.

## Before You Push

1. `mvn spotless:apply`
1. `mvn clean install` — must be clean: no compiler warnings, no PMD/checkstyle/SpotBugs findings,
   all tests passing.
1. Skim the diff for direct field dereferences on `@Nullable` DTO fields without a null check or
   `XSenseDtoUtil` fallback.
1. Update `README.md` if you changed the thing/channel model or configuration.

## Commits and Pull Requests

- `git commit -s` (DCO), never an AI co-author trailer.
- Group changes into a small number of logically coherent commits from a reviewer's point of
  view (e.g. "add feature X" + "add tests for X", not one commit per file). See the `oh-generic`
  skill's binding lifecycle guide for the full commit/PR/review process, including how to extend
  an existing PR without invalidating reviewer progress.
- Once a PR is open, keep its description current: scope, what changed, what it fixes — written
  for a reviewer coming in cold, not a changelog of your commits. If the PR closes an issue, add a
  comment on the issue linking back to the PR ("Addressed in PR #NNN").

## Getting Unstuck

Open questions and protocol details (bizCodes, MQTT topics, device model list) live in the
`oh-xsense` skill's `protocol.md` and `devices.md` if you have access to it, and in the reference
implementations linked from `architecture.md`. When in doubt about scope or a breaking change, ask
before implementing rather than guessing.
