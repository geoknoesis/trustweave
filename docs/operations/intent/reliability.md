# Ledger reliability and scale qualification

The SDK's PostgreSQL ledger uses a transactional occurrence counter to keep admission
independent of the length of a mandate's retained reservation history. An `AFTER INSERT`
trigger increments the counter only for inserted reservations. Duplicate inserts,
failed spending updates and rolled-back transactions do not consume occurrences.
Settlement and release preserve consumption and replay markers.

## Upgrade and durability requirements

1. Drain authorization and reconciliation writers before running `initializeSchema()`
   with the migration identity. It takes exclusive locks on both ledger tables, adds
   `occurrence_count`, backfills from **all** reservation states and installs the trigger
   in one transaction. A repeated migration does not reduce an existing count. Budget
   and evidence records are preserved; undated legacy records remain undated.
2. Budget time for the history scan and locks during migration. Each SQL statement has
   a ten-second timeout; a failed migration rolls back. Rehearse against a representative
   restored database before scheduling the upgrade. Very large migrations that cannot
   meet this bound need a separately reviewed migration procedure before rollout.
3. Keep the trigger enabled and the ledger tables append-only with respect to reservation
   identity. Do not reset counters or delete replay records while mandates can be presented.
   Routine authorization still needs SELECT/INSERT on reservations and SELECT/INSERT/UPDATE
   on accounts; reconciliation additionally needs UPDATE on reservations. The invoker-rights
   trigger does not grant callers new privileges. Runtime identities must not own tables
   or have DDL privileges. Backups must include schema, trigger and function definitions.
4. Upgrade all writers before admitting traffic. The trigger counts inserts from preceding
   writers, but that compatibility does not make writers without the current verification
   rules safe. Keep authorization fenced during any rollback or schema repair.
5. Ledger write transactions request `synchronous_commit=on`, preserving `remote_apply`
   when the session already requests it. The setting is transaction-local. This prevents
   an asynchronously configured pool from acknowledging an authorization before local
   WAL flush. It does not enable server `fsync`, reliable storage, synchronous replicas,
   WAL archiving or multi-region durability. Qualify those at the deployment layer.
6. Configure bounded pool size, connection acquisition, connection and socket timeouts.
   Each SQL query has a ten-second cancellation deadline. An operation has several queries;
   this is not a ten-second end-to-end request deadline. Enforce host admission/deadlines,
   retain reservations after unknown outcomes, and reconcile against authenticated evidence.

## Reproduce the component acceptance profile

Docker and JDK 21 are required. These tests create and remove only their own disposable
PostgreSQL 16 containers and synthetic data; no deployed database is targeted.

```shell
./gradlew :credentials:plugins:verifiable-intent:test
python scripts/check-reliability-evidence.py
```

Both CI and release-evidence run this profile and retain JSON measurements and PostgreSQL
recovery logs under `credentials/plugins/verifiable-intent/build/reports/reliability/`.
The named-test contract also requires execution, so a skipped test cannot satisfy the gate
using an old measurement file. Use a clean checkout/build for release evidence; running
the Python checker alone only validates existing artifacts, not their execution freshness.

| Scenario | Acceptance criterion |
| --- | --- |
| Multi-instance contention | 32 callers, four ledger objects, pool size eight; 4,096 initial requests yield exactly 2,048 authorizations; 1,024 later requests yield 512 after reconciliation; reserve p99 at most two seconds. |
| Sustained skew | At least 60 seconds, 80% of work on one mandate, at least 1,000 completed reserve/reconcile/replay workflows; workflow p99 at most five seconds; at most eight database connections and zero balance/count violations. |
| Long history | Migrate 100,000 legacy reservations; preserve occurrence limits, reject duplicate inserts and avoid history-count queries during admission. |
| Contended database row | A real SQL cancellation rolls back; an unrelated mandate progresses; a retry after confirmed rollback can succeed. |
| Privileges and atomicity | DML-only authorization identity can use the trigger; an injected spending failure rolls back both the reservation and counter. |
| WAL durability | Weakened session settings become synchronous for writes, stronger remote-apply settings survive, and session settings are restored after commit. |
| Process crash | Kill and restart the owned PostgreSQL container; all acknowledged synthetic records and replay rejection survive. |
| Physical recovery | Verify a physical base backup, recover three later reservations and terminal outcomes from archived WAL, stop at a named target, exclude a later record, compare complete account/reservation digests, then exercise replay and new admission. |
| Backup corruption | Modify a manifest-covered backup file without changing its length; `pg_verifybackup` must reject its checksum before the original file is restored and reverified. |
| Missing WAL | Remove the required post-backup segment; PostgreSQL must reject the unreachable target without becoming a writable primary. |

Timings are component measurements under test instrumentation, not production throughput,
RTO or RPO. Four objects share one pool/database in the load fixture; this is not a
distributed network-partition test. The one-minute workload is a regression gate, not a
multi-day soak. Hash comparison covers complete account and reservation rows; replay and
new-admission checks additionally prove that restored constraints and counters work.

## Deployment acceptance still required

The recovery fixture uses a local archive and a synthetic journal. A restored target can
deliberately omit later accepted payments; **keep execution and authorization fenced** until
the authoritative external journal and chosen recovery watermark agree. The SDK cannot
infer payments missing from an old backup. Never choose a restore point merely because it
starts successfully, or turn off a recovery target to make missing-WAL recovery succeed.

Before production rollout, record agreed capacity, p95/p99, availability, RPO and RTO;
qualify actual host verification costs, sustained traffic and tenant distribution; measure
encrypted remote archive lag, restore and reconciliation against the external journal;
exercise replica promotion, fencing, network partitions and storage exhaustion; and prove
monitoring and operator recovery through the intended deployment and on-call route.

PostgreSQL references: [continuous archiving and recovery](https://www.postgresql.org/docs/16/continuous-archiving.html),
[WAL durability settings](https://www.postgresql.org/docs/16/runtime-config-wal.html), and
[client transaction settings](https://www.postgresql.org/docs/16/runtime-config-client.html).
