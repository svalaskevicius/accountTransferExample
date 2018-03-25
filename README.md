# Money transfer between accounts

### Prerequisites 

 - JDK - Recommended Version 8, tested with 1.8.0_152
 - SBT (>= 1.0.0)

## Usage instructions

### Running service
Run tests:
```
sbt test
```

Run server using sbt:
```
sbt run
```

Package server and run using java:
```
sbt assembly
java -jar target/scala-2.12/account-transfers-assembly-0.1.0-SNAPSHOT.jar
```

### Example service usage using curl

```
# > curl -X POST localhost:8080/account5/register -d '{"initialBalance": 1000}'
{"message":"Account registered"}
# > curl -X POST localhost:8080/account6/register -d '{"initialBalance": 1000}'
{"message":"Account registered"}
# > curl -X GET localhost:8080/account5/balance
{"balance":1000}
# > curl -X GET localhost:8080/account6/balance
{"balance":1000}
# > curl -X POST localhost:8080/account5/transfer -d '{"accountTo": "account6", "amount": 500}'
{"message":"Transfer completed"}
# > curl -X GET localhost:8080/account5/balance
{"balance":500}
# > curl -X GET localhost:8080/account6/balance
{"balance":1500}
```

## Development considerations

### Why Event Sourcing?

As transferring money between accounts is a responsible operation, the service
provider has to be able to prove the correctness of the state at any given time.

EventSourcing makes logging events as the first class citizen of the application - 
the stored events drive the state change - thus, the logged data becomes the primary
source of truth.

### Why Tagless Final?

Abstracts our context type (e.g. Scala `Future`, Scalaz or Monix `Task`, or even identity)
into a type parameter, so the logic doesn't depend on it (and can be used with any provided
backend).

More info: https://softwaremill.com/free-monad-or-tagless-final-pres/

### Why the 3rd step to complete transaction?

You'll notice that after the target account has been credited, the source account 
completes the transaction (there is an event for this) - is this not too much for a simple transfer?

Because we cannot lock both Accounts at the same time, there are no consistency 
guarantees that the debited amount has been credited (e.g. in case of network issues or 
a system crash during the transaction). 

Knowing a list of started, but not completed transactions is useful in a reconciliation process.   

## Further improvements

### Snapshots

The current implementation is not optimised for performance at all.

In fact, the more changes a single account makes, the slower it will process new commands
because every time it will process the full history of its events.

There is a simple solution for this though, (not included in this example (yet?) to minimise the 
scope) - storing state snapshots of an aggregate periodically and only replaying 
events that happened after the snapshot was taken.

The main changes for this would be:
 - improve `AggregateLoader` to support taking and loading from snapshots;
 - improve `EventStorage` to version events and load/store the snapshots.

### Optimistic concurrency control

A potential improvement could also be to use optimistic concurrency control -
 https://en.wikipedia.org/wiki/Optimistic_concurrency_control

Of course, a durable EventStorage implementation would be a priority :)

### Versioning of events - changing the domain over time

One of the main fears for EventSourcing is handling changes in the domain model over time - how to add
new types of events, change existing ones, and how to still keep the system running?

While it is out of scope for this example, there are a few solutions available, e.g. versioning
 domain events, and handling different versions in the `applyEvent` function.

## License ##

This code is open source software licensed under the
[Apache-2.0](http://www.apache.org/licenses/LICENSE-2.0) license.
