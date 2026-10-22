package net.iegy.hisn.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterRuleEngineTest {
    @Test fun blocksDomainAnchoredRuleAndSubdomains() {
        val rules = FilterRuleEngine.parse(sequenceOf("||ads.example^", "||tracker.test^\$third-party"))
        assertTrue(rules.shouldBlock("https://ads.example/banner.js", "news.example"))
        assertTrue(rules.shouldBlock("https://cdn.ads.example/banner.js", "news.example"))
    }

    @Test fun pathRuleDoesNotBlockEveryPathOnTheSameHost() {
        val rules = FilterRuleEngine.parse(sequenceOf("||ads.example/banner"))
        assertTrue(rules.shouldBlock("https://ads.example/banner.js", "news.example"))
        assertFalse(rules.shouldBlock("https://ads.example/article.js", "news.example"))
    }

    @Test fun exceptionOverridesBlockRule() {
        val rules = FilterRuleEngine.parse(sequenceOf("||ads.example^", "@@||ads.example^"))
        assertFalse(rules.shouldBlock("https://ads.example/banner.js", "news.example"))
    }

    @Test fun domainOptionLimitsRuleToListedPage() {
        val rules = FilterRuleEngine.parse(sequenceOf("||tracker.example^\$domain=news.example"))
        assertTrue(rules.shouldBlock("https://tracker.example/pixel", "news.example"))
        assertFalse(rules.shouldBlock("https://tracker.example/pixel", "shop.example"))
    }

    @Test fun parsesCosmeticRulesAndBuildsScript() {
        val rules = FilterRuleEngine.parse(sequenceOf("##.ad-banner", "news.example###sponsored", "news.example#@#.allowed-ad"))
        val script = rules.cosmeticScript("news.example")
        assertTrue(script.contains(".ad-banner"))
        assertTrue(script.contains("#sponsored"))
        assertFalse(script.contains(".allowed-ad"))
    }

    @Test fun ignoresUnsafeCosmeticSelectors() {
        val rules = FilterRuleEngine.parse(sequenceOf("##iframe[src^=javascript:]"))
        assertTrue(rules.cosmeticScript("example.com").isEmpty())
    }
}
