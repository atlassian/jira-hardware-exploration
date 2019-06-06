### Usage

1. Set up AWS credentials
    * Locally according to [default AWS credentials]
    * Or let STS work out-of-the-box for [Bamboo]
2. Create a `jira-license.txt` file and fill it with a Jira license
    * BYO
    * Or reuse the one from [Bamboo]
3. Run `recommendHardware` Gradle task
    * From terminal: `./gradlew recommendHardware`
    * Or in short: `./gradlew recHar`
    * Or if you want to override Jira Software version without code changes: `./gradlew recHar -Dhwr.jsw.version=8.1.0`
    * Or from IntelliJ 2019+: `Run anything` (e.g. double tap `Ctrl`) and type `gradle recommendHardware`

This runs the entire hardware recommendation.
It will run all the tests, produce all the charts and print out the recommendations.
Currently it covers a single Jira profile per run (e.g. Jira L or Jira XL).

### Caching

At the beginning of the run, the results from S3 cache (if any) is downloaded and merged with local results.
Then the local results are reused. Only the results that are missing will be run.
Use this to your advantage. If the build flakes, rerun to just fill in the missing subresults.
Naturally, [Bamboo] does not have any local results so it will always download the entire S3 cache. 

The S3 cache requires read/write permissions to the S3 bucket,
so either match AWS creds to the bucket or change the bucket in [test source].
The `quicksilver-jhwr-cache-ireland` is owned by AWS account `jira-server-perf-dev (695067801333)`.
You can browse the shared cached results via S3 GUI. You can also make them public an link them: [a cached file].

If you customize the test and want to get a fresh batch of results, change the `taskName` from the [runtime config].

### Customization

Start from the [test source] and tweak existing knobs and levers or build new ones.

### Chat

Feel free to chat, ask for help, bounce ideas on the [JPT Community Slack].

[default AWS credentials]: https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
[Bamboo]: https://server-gdn-bamboo.internal.atlassian.com/browse/QUICK-JHWR
[test source]: src/test/kotlin/com/atlassian/performance/tools/hardware/HardwareRecommendationIT.kt
[a cached file]: https://s3-eu-west-1.amazonaws.com/quicksilver-jhwr-cache-ireland/QUICK-132-fix-v3/jira-exploration-chart.html
[runtime config]: src/test/kotlin/com/atlassian/performance/tools/hardware/IntegrationTestRuntime.kt
[JPT Community Slack]: http://go.atlassian.com/jpt-slack
