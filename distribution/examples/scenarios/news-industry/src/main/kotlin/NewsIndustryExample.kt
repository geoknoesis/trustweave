package org.trustweave.examples.news

import kotlinx.coroutines.runBlocking
import org.trustweave.examples.scenarios.runSignedScenario

/** An editor signs an article identifier, revision and author attribution. See this scenario's README for scope and commands. */
fun main(): Unit =
    runBlocking {
        runSignedScenario(
            name = "news-industry",
            credentialType = "ArticleProvenanceCredential",
            claims =
                linkedMapOf(
                    "articleId" to "article-demo-42",
                    "revision" to "2",
                    "authorName" to "Example Reporter",
                ),
            tamperedClaim = "revision",
            tamperedValue = "3",
        )
    }
