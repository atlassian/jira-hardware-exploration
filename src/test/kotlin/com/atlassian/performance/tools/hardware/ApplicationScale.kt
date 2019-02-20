package com.atlassian.performance.tools.hardware

import com.atlassian.performance.tools.infrastructure.api.dataset.Dataset
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad

class ApplicationScale(
    val description: String,
    val dataset: Dataset,
    val load: VirtualUserLoad,
    val vuNodes: Int
)