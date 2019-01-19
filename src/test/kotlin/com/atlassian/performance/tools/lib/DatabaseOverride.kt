package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.infrastructure.api.database.Database
import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset

fun Dataset.overrideDatabase(
    override: (Dataset) -> Database
): Dataset = Dataset(
    database = override(this),
    jiraHomeSource = jiraHomeSource,
    label = label
)
