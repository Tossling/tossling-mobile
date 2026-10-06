# Security

Tossling carries clipboards, files and keys, so security reports are welcome and handled first.

## Reporting a vulnerability

Please do not open a public issue. Use [private vulnerability reporting](https://github.com/tossling/tossling-mobile/security/advisories/new)
(the «Report a vulnerability» button on the Security tab) and include what you found, how to reproduce it and what an
attacker gains. You should get an answer within a week. Once a fix is released, the advisory is published with credit,
unless you prefer otherwise.

## Scope

- the Android app: storage of the room key, device keys and tokens, the pairing QR code, the persistent connection;
- the protocol it shares with the Mac: room keys, AES-256-GCM messages and attachments (TSY2), X25519 sealed rekey;
- content leaving the phone that should not, for example clipboard items marked sensitive.

Out of scope: a server operator reading metadata the server needs by design (which channels are used and when),
attacks that need an unlocked device the attacker already controls, and denial of service by flooding your own server.

## Supported versions

Only the latest release gets security fixes.
