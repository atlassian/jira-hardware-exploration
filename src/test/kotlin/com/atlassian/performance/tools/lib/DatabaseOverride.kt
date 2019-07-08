package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.infrastructure.api.jira.JiraHomeSource

fun Dataset.overrideDatabase(
    override: (Dataset) -> Database
): Dataset = Dataset(
    database = override(this),
    jiraHomeSource = jiraHomeSource,
    label = label
)

fun Dataset.overrideJiraHome(
    override: (Dataset) -> JiraHomeSource
): Dataset = Dataset(
    database = database,
    jiraHomeSource = override(this),
    label = label
)
