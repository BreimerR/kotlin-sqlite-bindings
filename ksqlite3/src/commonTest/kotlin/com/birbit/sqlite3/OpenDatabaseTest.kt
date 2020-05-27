/*
 * Copyright 2020 Google, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.birbit.sqlite3

import com.birbit.sqlite3.internal.AuthResult
import com.birbit.sqlite3.internal.ResultCode
import com.birbit.sqlite3.internal.SqliteException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenDatabaseTest {
    @Test
    fun openInMemory() {
        val conn = SqliteConnection.openConnection(":memory:")
        conn.close()
    }

    @Test
    fun openInvalidPath() {
        val result = kotlin.runCatching {
            SqliteConnection.openConnection("/::")
        }
        assertEquals(result.exceptionOrNull(), SqliteException(
            ResultCode.CANTOPEN,
            "could not open database at path  /::"
        ))
    }

    @Test
    fun openOnDisk() {
        withTmpFolder {
            val dbPath = getFilePath("testdb.db")
            val connection = SqliteConnection.openConnection(dbPath)
            connection.close()
            assertTrue(PlatformTestUtils.fileExists(dbPath), "no file in $dbPath")
        }
    }

    @Test
    fun readErrors() {
        SqliteConnection.openConnection(":memory:").use {
            val result = kotlin.runCatching {
                it.prepareStmt("SELECT * FROM nonExistingTable")
            }

            assertEquals(result.exceptionOrNull(), SqliteException(
                resultCode = ResultCode.ERROR,
                msg = "no such table: nonExistingTable"
            ))
            assertEquals(it.lastErrorCode(), ResultCode.ERROR)
            assertEquals(it.lastErrorMessage(), "no such table: nonExistingTable")
        }
    }

    @Test
    fun oneTimeQuery() {
        val res = SqliteConnection.openConnection(":memory:").use {
            it.query("SELECT ?, ?", listOf(3, "a")) {
                it.first().let {
                    it.readInt(0) to it.readString(1)
                }
            }
        }
        assertEquals(res, 3 to "a")
    }

    @Test
    fun authCallback() {
        val allParams = mutableListOf<Any>()
        SqliteConnection.openConnection(":memory:").use { conn ->
            conn.setAuthCallback {
                allParams.addAll(
                    listOfNotNull(it.param1, it.param2, it.param3, it.param4)
                )

                AuthResult.OK
            }
            conn.prepareStmt("SELECT * from sqlite_master").use { }
            assertTrue(allParams.contains("sqlite_master"), "auth callback has not been called")
        }
    }
}
