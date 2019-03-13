# Jobcoin Mixer
## Ratan Rai Sur

### Approach

In talking about my experience with the assignment, I'll draw attention to the discussion of **known bugs or vulnerabilities** in **bold** and my _intended next steps_ in _italics_.

The boilerplate has this set up as a command line interface with the `JobcoinClient` in the same process, which I believe is for the simplicity of the assignment. Typically the mixer would exist on a server. With more time, I could _factor out the mixer logic into it's own service_, making the CLI a client.

`MixerActor` is an actor that is responsible for keeping the payout balances, determining when transactions should be made, and for what amounts. It currently uses a naive approach that pays out a small constant amount to the destinations over time.

This approach has the benefit of always using the same transaction amounts, reducing the amount of information encoding in the amounts. However, **an adversary could narrow the field down by flagging the destinations that receive payments starting from the same epoch**. _Until the time-discounted value of the coins diminishes to some threshold, increasing the epoch length would increase the number of people included in each batch, benefitting the batch-mates in anonymity._ Another drawback to this method is the **potentially long pay out duration**.

In order to rapidly find issues, I created the `test-split` script to create new addresses and report the resulting balances in those addresses. I am a fan of writing tests first and implementing the business logic to make those tests pass. However, for speed of development I opted to create this script and inspect the output visually. I did however, think about how I might test this if it were a production system. _As long as the `JobcoinClient` was an actor as well, the `MixerActor` could be tested at its boundaries by inspecting what messages it was sending to the `JobcoinClient`._

Using my script, I quickly discovered that my assumption that I could make as many requests as I wanted and rely on the server logic to reject transactions with excessive amounts was wrong. When querying accounts, I began to see negative balances. To respond to this I set about adding balance checks in my client logic. However, the problem still persists as **multiple requests are asking for the balance of the pool address, seeing that the funds are sufficient, and then simultaneously withdrawing and overdrafting**. _Since the jobcoin server does not prevent the race condition, I would enforce a lock on accounts on my side._


Use `sbt run` to run the code, debug messages can be turned off using the `loglevel` option in `application.conf`.
