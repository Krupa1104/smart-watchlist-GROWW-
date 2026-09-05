# Scalability notes (temporary — will be folded into the root README)

This is a working note on deliberate scaling trade-offs made during the
hackathon build, specifically for Feature 5 (the simulated live/intraday
feed). It exists so the reasoning isn't lost before the full root README
gets written; nothing here is prescriptive beyond that.

## Current state: single-instance, in-memory

`TickSimulationService` keeps two things in plain JVM memory, with no
external store:

1. **Tick state** — each symbol's current simulated price, seeded from its
   real latest daily OHLC/NAV row and advanced by a bounded random walk
   (`ConcurrentHashMap<String, TickState>`, one map for stocks, one for
   funds).
2. **SSE subscribers** — the list of open `SseEmitter`s per watchlist
   (`ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>>`), used
   by the `@Scheduled` tick loop to know who to push each batch to.

This is intentional, not an oversight: the hackathon runs as a single
backend instance, so there is no coordination problem to solve yet, and
solving one that doesn't exist would just be unnecessary infrastructure
for a 72-hour build. It's called out explicitly here so it reads as a
trade-off, not a gap nobody noticed.

## Where this breaks down with more than one instance

Both structures above are **per-JVM**. Run two backend instances behind a
load balancer and:

- Each instance would independently seed and random-walk its own copy of
  every symbol's "current price" — two users hitting different instances
  would see two different, diverging simulated prices for the same
  symbol, which defeats the point of a shared "live" feed.
- An SSE connection is a long-lived HTTP connection pinned to whichever
  instance accepted it. An instance has no visibility into subscribers
  connected to a *different* instance, so a tick generated on instance A
  never reaches a client connected to instance B.

## The production fix: a shared broker, not more code in this class

The standard pattern for this — and what we'd actually build if this went
past a hackathon — is:

1. **Move tick generation out of per-instance state and into a single
   shared source of truth.** Either one designated instance (or a small
   dedicated worker) runs the tick loop and publishes each batch to a
   **Redis Pub/Sub** channel (or an equivalent lightweight broker), keyed
   by watchlist ID or symbol; or, more simply, the *current price* itself
   moves into a shared cache (Redis key per symbol) that any instance can
   read, with only one instance/worker responsible for advancing it.
2. **Every backend instance subscribes to that channel** and, for each
   message, fans it out to whichever of *its own* locally-connected SSE
   emitters care about that watchlist/symbol — i.e., the fan-out-to-
   browsers responsibility stays local and per-instance (that part
   doesn't need to change), but the tick *generation* and *distribution
   across instances* becomes the broker's job.
3. Subscriber bookkeeping stays local per instance (an emitter is only
   ever attached to the one instance that accepted its connection) — there's
   no need to make the subscriber list itself distributed, only the tick
   data feeding it.

This is a genuinely small, well-understood change in shape (swap "advance
my own in-memory map" for "read from / publish to Redis") — it's not
listed as a "nice to have someday" so much as "this is precisely the seam
we'd cut along," which is why it's worth writing down now rather than
guessing later.

## Explicitly out of scope for this pass

Redis, Kafka, WebSockets, or any other new infrastructure were
deliberately **not** introduced to make this note true — this is a
description of the seam, not an implementation of it. Introducing real
infrastructure for a single-instance hackathon demo would be the kind of
complexity the brief explicitly asks you to avoid adding where it isn't
earned yet.
