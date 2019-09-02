package com.atlassian.performance.tools.hardware.report

import com.atlassian.performance.tools.hardware.MockResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.util.Files
import org.junit.Test
import java.io.File

class RecommendationsTableTest {

    @Test
    fun shouldTabulate() {
        val recommendations = MockResult("/xl-quick-132-cache-db.json").readRecommendations()
        val output = Files.newTemporaryFile().toPath()

        val actualTable = RecommendationsTable().tabulate(recommendations, output)

        val expectedTable = File(javaClass.getResource("expected-recommendations-table.csv").toURI())
        assertThat(actualTable).hasSameContentAs(expectedTable)
    }
}
