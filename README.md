[![CircleCI](https://circleci.com/gh/atlassian/jira-hardware-exploration.svg?style=svg)](https://circleci.com/gh/atlassian/jira-hardware-exploration)

### Automated Jira hardware recommendations

This runs the entire hardware recommendation.
It will run all the tests, produce all the charts and print out the recommendations.
Currently it covers Jira L and Jira XL.

### Usage

#### Bamboo

1. Run the [Bamboo] plan
   * Optionally override the `HWR_PROPS` variable with a value like `-Dhwr.jsw.version=7.13.0`
2. Look for results in the Bamboo artifacts

If the Bamboo agent goes offline after 12 hours, rerun the plan. This can take 3-4 reruns.

#### Local

1. Navigate to the repo root
2. Set up AWS credentials according to [default AWS credentials]
3. Create a `jira-license.txt` file and fill it with a Jira license
4. Run `recommendHardware` Gradle task
    * From terminal: `./gradlew recommendHardware`
    * Or in short: `./gradlew recHar`
    * Or from IntelliJ 2019+: `Run anything` (e.g. double tap `Ctrl`) and type `gradle recommendHardware`
5. Read the logs and look for results in `build/jpt-workspace`

### Caching

At the beginning of the run, the results from S3 cache (if any) is downloaded and merged with local results.
Then the local results are reused. Only the results that are missing will be run.
Use this to your advantage. If the build flakes, rerun to just fill in the missing subresults.
Naturally, [Bamboo] does not have any local results so it will always download the entire S3 cache. 

The S3 cache requires read/write permissions to the S3 bucket,
so either match AWS creds to the bucket or change the bucket in [test source].
The `quicksilver-jhwr-cache-ireland` is owned by AWS account `jira-server-perf-dev (695067801333)`.
You can browse the shared cached results via S3 GUI. You can also make them public an link them: [a cached file].

If you customize the test and want to get a fresh batch of results, change the `val cacheKey` in the [test source].

### Customization

Start from the [test source] and tweak existing knobs and levers or build new ones.

### Chat

Feel free to chat, ask for help, bounce ideas on the [JPT Community Slack].

[default AWS credentials]: https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
[Bamboo]: https://server-gdn-bamboo.internal.atlassian.com/browse/QUICK-JHWR
[test source]: src/test/kotlin/com/atlassian/performance/tools/hardware/HardwareRecommendationIT.kt
[a cached file]: https://s3-eu-west-1.amazonaws.com/quicksilver-jhwr-cache-ireland/QUICK-132-fix-v3/jira-exploration-chart.html
[JPT Community Slack]: http://go.atlassian.com/jpt-slack
