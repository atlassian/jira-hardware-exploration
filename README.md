#### Repro

1. Navigate to the repo root
2. Set up AWS credentials according to [default AWS credentials]
3. Create a `jira-license.txt` file and fill it with a Jira license
4. Run `repro` Gradle task
    * From terminal: `./gradlew repro`
    * Or from IntelliJ 2019+: `Run anything` (e.g. double tap `Ctrl`) and type `gradle repro`
        * `Enter` will run
        * `Shift-Enter` will debug
5. Read the logs and look for results in `build/jpt-workspace`

### Customization

Start from the [test source] and tweak existing knobs and levers or build new ones.

[default AWS credentials]: https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
[test source]: src/test/kotlin/com/atlassian/performance/tools/hardware/BacklogPageIT.kt
