package com.atlassian.performance.tools.lib.infrastructure

import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset

class AdminDataset(
    val dataset: Dataset,
    val adminLogin: String,
    val adminPassword: String
)
