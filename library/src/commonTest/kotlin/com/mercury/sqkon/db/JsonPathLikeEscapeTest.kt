package com.mercury.sqkon.db

import com.mercury.sqkon.WildcardFieldObject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression test for the `fullkey LIKE ?` wildcard bug (#69).
 *
 * Predicates match the JSON node with `<tree>.fullkey LIKE ?`, binding the built path
 * (e.g. `$.user_name`) as the LIKE pattern. SQLite `LIKE` treats `_` as "any one char"
 * (and `%` as "any sequence"), with no ESCAPE clause, so a query on a snake_case field like
 * `user_name` also matched a sibling such as `userXname`. The fix escapes `_`/`%` in literal
 * path segments (preserving the deliberate `[%]` array wildcard) and appends `ESCAPE '\'`.
 */
class JsonPathLikeEscapeTest {

    private val mainScope = MainScope()
    private val driver = driverFactory().createDriver()
    private val entityQueries = EntityQueries(driver)
    private val metadataQueries = MetadataQueries(driver)
    private val storage = keyValueStorage<WildcardFieldObject>(
        "wildcard-field", entityQueries, metadataQueries, mainScope,
    )

    @After
    fun tearDown() {
        mainScope.cancel()
    }

    @Test
    fun eq_underscoreFieldName_doesNotMatchSiblingViaWildcard() = runTest {
        // "bob" lives only in the sibling key `userXname`, never in `user_name`.
        val obj = WildcardFieldObject(id = "1", user_name = "alice", userXname = "bob")
        storage.insert(obj.id, obj)

        // Querying user_name == "bob" must NOT match: the `_` in `$.user_name` was treated
        // as a wildcard, so `fullkey LIKE '$.user_name'` also matched `$.userXname` (value "bob").
        val falseMatch = storage.select(where = WildcardFieldObject::user_name eq "bob").first()
        assertEquals(0, falseMatch.size)

        // Sanity: the intended field still matches its real value.
        val trueMatch = storage.select(where = WildcardFieldObject::user_name eq "alice").first()
        assertEquals(1, trueMatch.size)
    }

    @Test
    fun eq_underscoreListField_matchesElementDespiteFullkeyQuoting() = runTest {
        // json_tree emits this element as `$."my_tags"[0]` (quoted key + index). The built
        // pattern must reproduce the quoting, escape the `_`, and keep `[%]` as a wildcard,
        // otherwise the list element never matches.
        val obj = WildcardFieldObject(id = "1", user_name = "alice", userXname = "bob", my_tags = listOf("target"))
        storage.insert(obj.id, obj)

        val match = storage.select(where = WildcardFieldObject::my_tags eq "target").first()
        assertEquals(1, match.size)

        val noMatch = storage.select(where = WildcardFieldObject::my_tags eq "absent").first()
        assertEquals(0, noMatch.size)
    }
}
