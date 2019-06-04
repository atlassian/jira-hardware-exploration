### Usage

1. Set up AWS credentials
    * Locally according to [default AWS credentials]
    * Or let STS work out-of-the-box for [Bamboo]
2. Create a `jira-license.txt` file and fill it with a Jira license
    * BYO
    * Or reuse the one from [Bamboo]
3. Run `exploreHardware` Gradle task
    * From terminal: `./gradlew exploreHardware`
    * Or from IntelliJ: Double tap `Ctrl` and type `gradle exploreHardware`.

[default AWS credentials]: https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
[Bamboo]: https://server-gdn-bamboo.internal.atlassian.com/browse/QUICK-JHWR

### Caching

Results are automatically cached and reused. It works both locally and remotely. If you have produced local results,
and rerun the tests, they will be reused. If someone else (or Bamboo) also produced some results, they will be reused.
Use this to your advantage. If the build flakes, rerun to just fill in the missing subresults.

You can browse the shared cached results via S3 GUI.

If you customize the test and want to get a fresh batch of results, change the `taskName` to something different.

[`taskName`]: src/test/kotlin/com/atlassian/performance/tools/hardware/IntegrationTestRuntime.kt

### Customization

Start form the [test-source] and reuse existing levers or build your own.

[test source]: src/test/kotlin/com/atlassian/performance/tools/hardware/HardwareExplorationIT.kt
