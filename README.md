# Jobcoin Mixer
## Ratan Rai Sur

### Engineering Approach

In talking about my experience with the assignment, I'll draw attention to the discussion of **known bugs or vulnerabilities** in **bold** and my _intended next steps_ in _italics_.

The boilerplate has this set up as a command line interface with the `JobcoinClient` in the same process, which I believe is for the simplicity of the assignment. Typically the mixer would exist on a server. With more time, I could _factor out the mixer logic into it's own service_, making the CLI a client.

`MixerActor` is an actor that is responsible for keeping the payout balances, determining when transactions should be made, and for what amounts. It currently uses a naive approach that pays out a small constant amount to the destinations over time.

This approach has the benefit of always using the same transaction amounts, reducing the amount of information encoding in the amounts. However, **an adversary could narrow the field down by flagging the destinations that receive payments starting from the same epoch**. _Until the time-discounted value of the coins diminishes to some threshold, increasing the epoch length would increase the number of people included in each batch, benefitting the batch-mates in anonymity._ Another drawback to this method is the **potentially long pay out duration**.

In order to rapidly find issues, I created the `test-split` script to create new addresses and report the resulting balances in those addresses. I am a fan of writing tests first and implementing the business logic to make those tests pass. However, for speed of development I opted to create this script and inspect the output visually. I did however, think about how I might test this if it were a production system. _As long as the `JobcoinClient` was an actor as well, the `MixerActor` could be tested at its boundaries by inspecting what messages it was sending to the `JobcoinClient`._

Using my script, I quickly discovered that my assumption that I could make as many requests as I wanted and rely on the server logic to reject transactions with excessive amounts was wrong. When querying accounts, I began to see negative balances. To respond to this I set about adding balance checks in my client logic. However, the problem still persists as **multiple requests are asking for the balance of the pool address, seeing that the funds are sufficient, and then simultaneously withdrawing and overdrafting**. _Since the jobcoin server does not prevent the race condition, I would enforce a lock on accounts on my side._


Use `sbt run` to run the code, debug messages can be turned off using the `loglevel` option in `application.conf`.


### Mixing Algorithm

Although I implemented a very basic mixing algorithm, I wanted to explain some thoughts I had during the process and an idea I had for what I could implement in the future. One security parameter in the hands of the user is the ratio of amount of coins to be mixed to the number of destination addresses provided, which I'll name the dilution ratio. As this ratio increases, the lifetime flow into the account exceeds those of the accounts in a lower ratio association. For example, say three people were using the mixing service and the first two provide five destination addresses each to mix 10 coins, while the last provides only two to mix 30. In the long run, the last user's accounts will be identified because at least one account in the output set will have a rarifyingly high balance.

For analysis, let's consider that all payouts happen instantaneously to all the destination addresses. Given enough time, an adversary could see the lifetime flow into the destination accounts and treat it as a discrete transfer regardless.

I set about thinking of improvements that would at least provide users with a highly anonymous account at the risk of maybe leaving some output accounts highly coupled with the original user (which could be mixed back in). My first pass was to use some form of an average (not necessarily arithmetic mean) dilution ratio to set the payout size. This has the advtantage that every user receives a transaction for 10, but you can see that most of the 10 transactions belong to B so any account with 10 has a 50% chance of belonging to B.

|User|# of Coins|# of Outputs|Dest 1|Dest 2|Dest 3|Dest 4|
|----|----------|------------|------|------|------|------|
|A   |        50|  2         |    10|    40|   N/A|   N/A|
|B   |        30|  3         |    10|    10|    10|   N/A|
|C   |        21|  4         |    10|    10|     1|     0|

In this example, Dest 2 clearly belongs to A but that is the price is to pay for using a higher dilution ratio. The mixing service could communicate to the user that their Dest 2 account is highly coupled and suggest remixing.


So we also want each user to receive unique amounts to each of their destination addresses. To aleviate this, we can use an arithmetic series mixing scheme:

* Arithmetic series: `S_n = (1/2)n(a_1 + a_n)` where `n = # of elements in sequence, a_1 = first element, a_n = nth element`
* `a_n = a_1 = (n-1)d` where `d` is the step size of the sequence
* If `a_1 = d`, `S_n = (1/2)(n^2 + n)d`
* Computing `d` using `n` as the number of output addresses and `S_n` as the total number of coins to mix, we get, after flooring:

|User|# of Coins|# of Outputs| d|
|----|----------|------------|--|
|A   |        50|  2         |16|
|B   |        30|  3         | 5|
|C   |        21|  4         | 2|

Taking the median `d` and generating the sequence, we get: `5, 10, 15, ...`
Each user's last address is their least anonymous and simply contains a dump of the remaining coins.

|User|# of Coins|# of Outputs|Dest 1|Dest 2|Dest 3|Dest 4|
|----|----------|------------|------|------|------|------|
|A   |        50|  2         |    5|    45|   N/A|   N/A|
|B   |        30|  3         |    5|    10|    15|   N/A|
|C   |        21|  4         |    5|    10|     6|     0|


This approach prefers leaving some addresses with maximal anonymity (those in Dest1's) at the cost of other addresses. I'm sure there are some vulnerabilities in this newest approach but my intuition led me down this path and it was fun to flesh it out to see why I thought the way I did!

All of these approaches could benefit from adding noise and randomness in various places like payout timing, fees, etc. but those can always be retrofitted in and I wanted to focus on thinking of an approach that created ambiguity even with a deterministic process.
